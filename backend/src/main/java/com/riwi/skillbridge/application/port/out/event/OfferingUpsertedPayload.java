package com.riwi.skillbridge.application.port.out.event;

import com.riwi.skillbridge.domain.model.Offering;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Estado completo de una oferta (ECST) para read models de otros servicios (HU-104, p. ej. {@code ai-service}).
 * Se publica como {@code OfferingUpserted} v1 en {@code booking-events} con clave {@code offeringId}.
 * Ver {@code specs/104-microservices-gateway/contracts/kafka-topics.md}.
 */
public record OfferingUpsertedPayload(
    UUID id,
    UUID providerId,
    String title,
    String description,
    String category,
    BigDecimal price,
    boolean active,
    String mode,
    int durationHours
) {
    public static OfferingUpsertedPayload from(Offering o) {
        return new OfferingUpsertedPayload(o.id(), o.providerId(), o.title(), o.description(), o.category(),
            o.price(), o.active(), o.mode() == null ? null : o.mode().name(), o.durationHours());
    }
}
