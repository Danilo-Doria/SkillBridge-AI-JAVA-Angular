package com.riwi.skillbridge.payment.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Proyección local de una oferta, construida con {@code OfferingUpserted} v1 de {@code booking-events}. */
public record OfferingView(
    UUID id,
    UUID providerId,
    String title,
    BigDecimal price,
    boolean active,
    MentorshipMode mode,
    int durationHours
) {}
