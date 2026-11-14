package com.riwi.skillbridge.domain.event;

import java.util.UUID;

public record PaymentRejected(
    UUID bookingId,
    String reason,
    // HU-104: id determinista del evento (ver PaymentEventIds); null = el publicador genera uno.
    UUID eventId
) {
    public PaymentRejected(UUID bookingId, String reason) {
        this(bookingId, reason, null);
    }
}
