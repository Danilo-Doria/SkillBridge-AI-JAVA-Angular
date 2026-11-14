package com.riwi.skillbridge.ai.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.ai.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.ai.application.port.in.SyncOfferingViewUseCase;
import com.riwi.skillbridge.ai.application.port.in.SyncUserAccessUseCase;
import com.riwi.skillbridge.ai.domain.model.AccessStatus;
import com.riwi.skillbridge.ai.domain.model.MentorshipMode;
import com.riwi.skillbridge.ai.domain.model.Offering;
import com.riwi.skillbridge.ai.domain.model.UserAccess;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consume {@code booking-events} (group-id {@code ai-service}) y proyecta {@code OfferingUpserted} sobre
 * {@code ai.offering_view} y {@code UserAccessChanged} sobre {@code ai.user_access}. Otros {@code eventType} se
 * ignoran. Idempotente: el upsert no reaplica un {@code eventId} ya aplicado ni un evento más antiguo.
 *
 * <p>{@code UserAccessChanged} v1: clave {@code userId}, payload {@code {userId, status, role}} (status
 * {@code ACTIVE|SUSPENDED}); {@code userId} ausente cae a {@code aggregateId}.
 *
 * <p>Envelope {@code BusinessEvent} serializado por el monolito: el payload llega como objeto plano (mezclado con
 * los campos de actor), con {@code id, providerId, title, description, category, price, active, mode, durationHours}.
 * Un mensaje mal formado lanza {@link IllegalArgumentException}/{@link IOException} (no reintentables) y termina en
 * {@code ai-service.booking-events.DLT}.
 */
@Component
public class OfferingUpsertedConsumer {

    static final String EVENT_TYPE = "OfferingUpserted";
    static final String USER_ACCESS_CHANGED = "UserAccessChanged";
    private static final Logger log = LoggerFactory.getLogger(OfferingUpsertedConsumer.class);

    private final SyncOfferingViewUseCase sync;
    private final SyncUserAccessUseCase userAccess;
    private final ObjectMapper objectMapper;

    public OfferingUpsertedConsumer(SyncOfferingViewUseCase sync, SyncUserAccessUseCase userAccess,
                                    ObjectMapper objectMapper) {
        this.sync = sync;
        this.userAccess = userAccess;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            id = "ai-service-offering-view",
            topics = "${app.kafka.topic.booking-events}",
            groupId = "${app.kafka.consumer.group-id:ai-service}",
            containerFactory = "offeringEventsListenerFactory")
    public void onMessage(ConsumerRecord<String, String> record) throws IOException {
        if (record.value() == null || record.value().isBlank()) {
            return;
        }
        JsonNode root = objectMapper.readTree(record.value());
        String eventType = text(root, "eventType");
        if (!EVENT_TYPE.equals(eventType) && !USER_ACCESS_CHANGED.equals(eventType)) {
            return; // otros eventos del topic compartido: no aplican a estos read models
        }
        String correlationId = text(root, "correlationId");
        if (correlationId != null) {
            CorrelationIdHolder.set(correlationId);
            MDC.put("correlationId", correlationId);
        }
        try {
            UUID eventId = uuid(root, "eventId");
            // El JsonSerializer del monolito puede escribir Instant como decimal (segundos.nanos) o ISO-8601.
            JsonNode occurredNode = root.get("occurredAt");
            if (occurredNode == null || occurredNode.isNull()) {
                throw new IllegalArgumentException("Missing field occurredAt");
            }
            Instant occurredAt = objectMapper.treeToValue(occurredNode, Instant.class);
            JsonNode p = root.path("payload");
            if (!p.isObject()) {
                throw new IllegalArgumentException(eventType + " without payload");
            }
            if (USER_ACCESS_CHANGED.equals(eventType)) {
                applyUserAccess(root, p, eventId, occurredAt, record);
                return;
            }
            Offering offering = new Offering(
                    uuid(p, "id"),
                    uuid(p, "providerId"),
                    text(p, "title"),
                    text(p, "description"),
                    text(p, "category"),
                    p.hasNonNull("price") ? new BigDecimal(p.get("price").asText()) : null,
                    p.path("active").asBoolean(false),
                    p.hasNonNull("mode") ? MentorshipMode.valueOf(p.get("mode").asText()) : null,
                    p.path("durationHours").asInt(0));
            boolean changed = sync.apply(offering, eventId, occurredAt);
            log.info("OfferingUpserted offeringId={} eventId={} applied={} partition={} offset={}",
                    offering.id(), eventId, changed, record.partition(), record.offset());
        } finally {
            CorrelationIdHolder.clear();
            MDC.remove("correlationId");
        }
    }

    private void applyUserAccess(JsonNode root, JsonNode p, UUID eventId, Instant occurredAt,
                                 ConsumerRecord<String, String> record) {
        UUID userId = p.hasNonNull("userId") ? uuid(p, "userId") : uuid(root, "aggregateId");
        UserAccess access = new UserAccess(userId, AccessStatus.valueOf(required(p, "status")), required(p, "role"));
        boolean changed = userAccess.apply(access, eventId, occurredAt);
        log.info("UserAccessChanged userId={} status={} role={} eventId={} applied={} partition={} offset={}",
                userId, access.status(), access.role(), eventId, changed, record.partition(), record.offset());
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
        return UUID.fromString(required(node, field));
    }
}
