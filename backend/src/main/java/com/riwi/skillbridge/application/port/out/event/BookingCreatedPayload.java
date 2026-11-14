package com.riwi.skillbridge.application.port.out.event;

import java.time.Instant;
import java.util.UUID;

public record BookingCreatedPayload(
    UUID bookingId,
    UUID offeringId,
    UUID customerId,
    Instant scheduledAt,
    String status,
    // HU-104 (aditivo): destinatario para servicios que no leen la tabla users (payment-service).
    String customerEmail
) {
    public BookingCreatedPayload(UUID bookingId, UUID offeringId, UUID customerId, Instant scheduledAt, String status) {
        this(bookingId, offeringId, customerId, scheduledAt, status, null);
    }
}
