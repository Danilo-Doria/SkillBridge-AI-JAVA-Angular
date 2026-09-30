package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Booking(
        UUID id,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        BookingStatus status
) {}
