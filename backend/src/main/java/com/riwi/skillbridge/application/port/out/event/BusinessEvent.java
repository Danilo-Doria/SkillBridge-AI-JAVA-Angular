package com.riwi.skillbridge.application.port.out.event;

import java.time.Instant;
import java.util.UUID;

public record BusinessEvent<T>(
    UUID eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String correlationId,
    int version,
    T payload
) {
    public BusinessEvent {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (eventType == null) throw new IllegalArgumentException("eventType cannot be null");
        if (aggregateId == null) throw new IllegalArgumentException("aggregateId cannot be null");
        if (aggregateType == null) throw new IllegalArgumentException("aggregateType cannot be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt cannot be null");
    }
}
