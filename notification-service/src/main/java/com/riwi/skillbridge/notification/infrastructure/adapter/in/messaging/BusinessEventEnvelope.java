package com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de lectura del envelope {@code BusinessEvent} (ADR-0006 §4) que publica el monolito en
 * {@code notification-requests}. Es un DTO propio (no hay dependencia de compilación con {@code backend/}):
 * mismos nombres JSON; el {@code payload} se mantiene como árbol y se convierte después.
 * Los campos de actor ({@code actorUserId}, ...) y cualquier campo nuevo se ignoran.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessEventEnvelope(
    UUID eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String correlationId,
    Integer version,
    JsonNode payload
) {
}
