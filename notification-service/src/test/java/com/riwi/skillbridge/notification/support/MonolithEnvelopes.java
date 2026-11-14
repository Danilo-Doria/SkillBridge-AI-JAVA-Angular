package com.riwi.skillbridge.notification.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reproduce, solo para tests, cómo publica el monolito en {@code notification-requests}
 * ({@code KafkaNotificationPublisher} + {@code KafkaEventHeaders.record} + {@code BusinessEvent} con
 * {@code getPayloadForAudit}, serializado por el {@code JsonSerializer} de spring-kafka que configura
 * {@code spring.kafka.producer.value-serializer}). Copia de forma, sin dependencia de compilación con backend/.
 */
public final class MonolithEnvelopes {

    private MonolithEnvelopes() {
    }

    /** Copia de {@code BusinessEvent} (record + payload WRITE_ONLY + getter {@code payload} aplanado). */
    public record BusinessEventCopy<T>(
        UUID eventId, String eventType, String aggregateId, String aggregateType, Instant occurredAt,
        String correlationId, int version,
        @JsonProperty(value = "payload", access = JsonProperty.Access.WRITE_ONLY) T payload,
        String actorUserId, String actorUsername, String actorRole, String action, String resource,
        String resourceId) {

        @JsonProperty("payload")
        public Map<String, Object> getPayloadForAudit() {
            Map<String, Object> map = new HashMap<>();
            if (payload != null) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payloadMap = mapper.convertValue(payload, Map.class);
                    if (payloadMap != null) map.putAll(payloadMap);
                } catch (Exception e) {
                    map.put("data", payload);
                }
            }
            return map;
        }
    }

    /** Copia exacta de {@code NotificationMessage} actual del monolito (sin destinatario). */
    public record CurrentNotificationMessage(UUID eventId, UUID bookingId, UUID userId,
                                             NotificationType notificationType, Instant occurredAt,
                                             Instant paymentDeadline) {
    }

    /** Forma propuesta (ECST): {@code NotificationMessage} + destinatario. */
    public record EnrichedNotificationMessage(UUID eventId, UUID bookingId, UUID userId,
                                              NotificationType notificationType, Instant occurredAt,
                                              Instant paymentDeadline, String recipientEmail,
                                              String recipientName) {
    }

    public static String eventTypeFor(NotificationType type) {
        return switch (type) {
            case BOOKING_CREATED -> "BookingCreatedNotification";
            case BOOKING_CANCELLED -> "BookingCancelledNotification";
            case PAYMENT_REMINDER -> "PaymentReminderNotification";
        };
    }

    public static BusinessEventCopy<Object> envelope(UUID eventId, UUID bookingId, NotificationType type,
                                                     Instant occurredAt, String correlationId, Object payload) {
        return new BusinessEventCopy<>(eventId, eventTypeFor(type), bookingId.toString(), "Booking", occurredAt,
            correlationId, 1, payload, null, null, null, null, null, null);
    }

    /** Registro como {@code KafkaEventHeaders.record}: clave bookingId + cabeceras del contrato + {@code __TypeId__}. */
    public static ProducerRecord<String, byte[]> record(String topic, BusinessEventCopy<?> event) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, event.aggregateId(), null);
        Headers headers = record.headers();
        put(headers, "X-Correlation-Id", event.correlationId());
        put(headers, "eventId", event.eventId().toString());
        put(headers, "eventType", event.eventType());
        put(headers, "schemaVersion", Integer.toString(event.version()));
        try (JsonSerializer<Object> serializer = new JsonSerializer<>()) {
            byte[] value = serializer.serialize(topic, headers, event);
            return new ProducerRecord<>(topic, null, event.aggregateId(), value, headers);
        }
    }

    private static void put(Headers headers, String name, String value) {
        if (value != null) headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
