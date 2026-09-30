package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.UUID;

public record BookingCreatedEvent(
        UUID bookingId,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        Instant occurredAt
) {}
