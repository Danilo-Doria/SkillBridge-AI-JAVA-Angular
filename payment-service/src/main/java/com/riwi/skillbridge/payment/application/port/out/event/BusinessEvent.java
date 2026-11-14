package com.riwi.skillbridge.payment.application.port.out.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Copia literal del envelope {@code BusinessEvent} del monolito (sin dependencia de compilación con backend/):
 * el JSON en {@code payment-events} debe ser byte a byte el mismo que producía el monolito, incluido el payload
 * aplanado por {@link #getPayloadForAudit()}.
 */
public record BusinessEvent<T>(
    UUID eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String correlationId,
    int version,

    // Read-only from Kafka's point of view: serialized through getPayloadForAudit() (merged with actor fields),
    // but still deserialized back into this component.
    @JsonProperty(value = "payload", access = JsonProperty.Access.WRITE_ONLY)
    T payload,

    String actorUserId,
    String actorUsername,
    String actorRole,
    String action,
    String resource,
    String resourceId
) {
    public BusinessEvent {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (eventType == null) throw new IllegalArgumentException("eventType cannot be null");
        if (aggregateId == null) throw new IllegalArgumentException("aggregateId cannot be null");
        if (aggregateType == null) throw new IllegalArgumentException("aggregateType cannot be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt cannot be null");
    }

    @JsonProperty("payload")
    public Map<String, Object> getPayloadForAudit() {
        Map<String, Object> map = new HashMap<>();
        if (payload != null) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = mapper.convertValue(payload, Map.class);
                if (payloadMap != null) {
                    map.putAll(payloadMap);
                }
            } catch (Exception e) {
                map.put("data", payload);
            }
        }
        if (actorUserId != null) map.put("actorUserId", actorUserId);
        if (actorUsername != null) map.put("actorUsername", actorUsername);
        if (actorRole != null) map.put("actorRole", actorRole);
        if (action != null) map.put("action", action);
        if (resource != null) map.put("resource", resource);
        if (resourceId != null) map.put("resourceId", resourceId);
        return map;
    }
}
