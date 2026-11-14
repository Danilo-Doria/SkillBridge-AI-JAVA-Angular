package com.riwi.skillbridge.application.port.out.event;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-104 (E1): estado completo de una reserva tras una transición ({@code BookingStatusChanged} v1) o en el snapshot
 * de arranque ({@code BookingStateSnapshot} v1). Lo consume {@code payment-service} para su read model; clave Kafka
 * {@code bookingId} en {@code booking-events}.
 */
public record BookingStatusChangedPayload(
    UUID bookingId,
    UUID offeringId,
    UUID customerId,
    Instant scheduledAt,
    String status,
    String customerEmail
) { }
