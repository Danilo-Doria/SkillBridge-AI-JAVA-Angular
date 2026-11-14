package com.riwi.skillbridge.payment.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Proyección local de una reserva del monolito, construida solo con eventos de {@code booking-events}
 * (FR-010: payment-service no lee la BD del monolito).
 *
 * @param customerEmail puede ser {@code null} si el evento no lo transportaba
 * @param scheduledAt   puede ser {@code null} si solo se recibió {@code BookingCancelled}
 * @param status        estado del monolito ({@code CREATED}, {@code CANCELLED}, ...)
 */
public record BookingView(
    UUID bookingId,
    UUID offeringId,
    UUID customerId,
    String customerEmail,
    Instant scheduledAt,
    String status
) {
    public static final String CREATED = "CREATED";
    public static final String CANCELLED = "CANCELLED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String COMPLETED = "COMPLETED";

    public boolean isOwnedBy(UUID actorId) {
        return actorId != null && actorId.equals(customerId);
    }

    public boolean isCancelled() {
        return CANCELLED.equals(status);
    }

    /** CONFIRMED o COMPLETED en el monolito: el pago ya se aprobó ({@code BookingStatusChanged}). */
    public boolean isPaid() {
        return CONFIRMED.equals(status) || COMPLETED.equals(status);
    }

    public boolean isAwaitingPayment() {
        return CREATED.equals(status);
    }

    /** Igual que {@code Booking.isPaymentWindowExpired} del monolito: en CREATED y a menos de N horas de la sesión. */
    public boolean isPaymentWindowExpired(Instant now, Duration window) {
        return isAwaitingPayment() && scheduledAt != null && !now.isBefore(scheduledAt.minus(window));
    }
}
