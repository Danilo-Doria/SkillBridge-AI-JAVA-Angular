package com.riwi.skillbridge.payment.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.payment.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.payment.application.port.in.SyncReadModelUseCase;
import com.riwi.skillbridge.payment.application.port.in.SyncUserAccessUseCase;
import com.riwi.skillbridge.payment.domain.model.AccessStatus;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.MentorshipMode;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import com.riwi.skillbridge.payment.domain.model.UserAccess;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Consume {@code booking-events} (group-id {@code payment-service}) y proyecta sobre el schema {@code payment}:
 * <ul>
 *   <li>{@code BookingCreated} v1 → booking_view (bookingId, offeringId, customerId, scheduledAt, status).
 *       {@code customerEmail} se toma de {@code actorUsername} SOLO si {@code actorUserId == customerId}
 *       (en el monolito el actor de BookingCreated es el propio cliente y actorUsername es su email).</li>
 *   <li>{@code BookingCancelled} v1 → booking_view.status = CANCELLED.</li>
 *   <li>{@code BookingStatusChanged} v1 (cada transición) y {@code BookingStateSnapshot} v1 (snapshot de reservas
 *       abiertas al arrancar el monolito) → upsert completo de booking_view, mismo payload que BookingCreated más
 *       {@code customerEmail} opcional; {@code status} obligatorio y textual (enum BookingStatus del monolito).</li>
 *   <li>{@code OfferingUpserted} v1 → offering_view.</li>
 *   <li>{@code UserAccessChanged} v1 → user_access ({@code {userId, status, role}}; revocación de JWT, hallazgo S1).</li>
 * </ul>
 * El resto de eventType del topic compartido se ignora. Campos desconocidos se ignoran (el payload llega aplanado con
 * los campos de actor por {@code BusinessEvent.getPayloadForAudit}). Idempotente por eventId/occurredAt.
 * Mensajes mal formados: {@link IllegalArgumentException}/{@link IOException} → sin reintento → DLT propia.
 */
@Component
public class BookingEventsConsumer {

    static final String BOOKING_CREATED = "BookingCreated";
    static final String BOOKING_CANCELLED = "BookingCancelled";
    static final String OFFERING_UPSERTED = "OfferingUpserted";
    static final String BOOKING_STATUS_CHANGED = "BookingStatusChanged";
    static final String BOOKING_STATE_SNAPSHOT = "BookingStateSnapshot";
    static final String USER_ACCESS_CHANGED = "UserAccessChanged";
    private static final Set<String> HANDLED = Set.of(BOOKING_CREATED, BOOKING_CANCELLED, OFFERING_UPSERTED,
            BOOKING_STATUS_CHANGED, BOOKING_STATE_SNAPSHOT, USER_ACCESS_CHANGED);
    private static final Logger log = LoggerFactory.getLogger(BookingEventsConsumer.class);

    // Lector propio: los Instant del payload llegan como decimales (segundos.nanos); sin BigDecimal se perdería precisión.
    private final ObjectMapper reader = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final SyncReadModelUseCase sync;
    private final SyncUserAccessUseCase userAccess;

    public BookingEventsConsumer(SyncReadModelUseCase sync, SyncUserAccessUseCase userAccess) {
        this.sync = sync;
        this.userAccess = userAccess;
    }

    @KafkaListener(
            id = "payment-service-booking-events",
            topics = "${app.kafka.topic.booking-events}",
            groupId = "${app.kafka.consumer.group-id:payment-service}",
            containerFactory = "bookingEventsListenerFactory")
    public void onMessage(ConsumerRecord<String, String> record) throws IOException {
        if (record.value() == null || record.value().isBlank()) {
            return;
        }
        JsonNode root = reader.readTree(record.value());
        String eventType = text(root, "eventType");
        if (eventType == null || !HANDLED.contains(eventType)) {
            return;
        }
        String correlationId = text(root, "correlationId");
        if (correlationId != null) {
            CorrelationIdHolder.set(correlationId);
            MDC.put("correlationId", correlationId);
        }
        try {
            UUID eventId = uuid(root, "eventId");
            Instant occurredAt = instant(root.get("occurredAt"));
            if (occurredAt == null) {
                throw new IllegalArgumentException("Missing field occurredAt");
            }
            JsonNode p = root.path("payload");
            if (!p.isObject()) {
                throw new IllegalArgumentException(eventType + " without payload");
            }
            boolean changed = switch (eventType) {
                case BOOKING_CREATED -> sync.bookingCreated(bookingView(root, p,
                        p.hasNonNull("status") ? p.get("status").asText() : BookingView.CREATED), eventId, occurredAt);
                case BOOKING_STATUS_CHANGED, BOOKING_STATE_SNAPSHOT ->
                        sync.bookingStateChanged(bookingView(root, p, required(p, "status")), eventId, occurredAt);
                case USER_ACCESS_CHANGED -> userAccess.apply(new UserAccess(
                        p.hasNonNull("userId") ? uuid(p, "userId") : uuid(root, "aggregateId"),
                        AccessStatus.valueOf(required(p, "status")), required(p, "role")), eventId, occurredAt);
                case BOOKING_CANCELLED -> sync.bookingCancelled(uuid(p, "bookingId"), optionalUuid(p, "offeringId"),
                        uuid(p, "customerId"), eventId, occurredAt);
                default -> sync.offeringUpserted(new OfferingView(
                        uuid(p, "id"),
                        optionalUuid(p, "providerId"),
                        text(p, "title"),
                        p.hasNonNull("price") ? new BigDecimal(p.get("price").asText()) : null,
                        p.path("active").asBoolean(false),
                        p.hasNonNull("mode") ? MentorshipMode.valueOf(p.get("mode").asText()) : null,
                        p.path("durationHours").asInt(0)), eventId, occurredAt);
            };
            log.info("{} eventId={} key={} applied={} partition={} offset={}",
                    eventType, eventId, record.key(), changed, record.partition(), record.offset());
        } finally {
            CorrelationIdHolder.clear();
            MDC.remove("correlationId");
        }
    }

    private static BookingView bookingView(JsonNode root, JsonNode p, String status) {
        UUID customerId = uuid(p, "customerId");
        // HU-104: el monolito publica customerEmail explícito; fallback al actor solo si es el propio cliente.
        String email = text(p, "customerEmail");
        if (email == null && customerId.toString().equals(text(root, "actorUserId"))) {
            email = text(root, "actorUsername");
        }
        return new BookingView(uuid(p, "bookingId"), optionalUuid(p, "offeringId"), customerId, email,
                instant(p.get("scheduledAt")), status);
    }

    /** Acepta segundos decimales (Jackson con timestamps) o ISO-8601. */
    static Instant instant(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber() || (node.isTextual() && node.asText().matches("-?\\d+(\\.\\d+)?"))) {
            BigDecimal seconds = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
            long whole = seconds.longValue();
            int nanos = seconds.subtract(BigDecimal.valueOf(whole)).movePointRight(9).intValue();
            return Instant.ofEpochSecond(whole, nanos);
        }
        return Instant.parse(node.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String required(JsonNode node, String field) {
        String v = text(node, field);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing field " + field);
        }
        return v;
    }

    private static UUID uuid(JsonNode node, String field) {
        String v = text(node, field);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing field " + field);
        }
        return UUID.fromString(v);
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        String v = text(node, field);
        return v == null || v.isBlank() ? null : UUID.fromString(v);
    }
}
