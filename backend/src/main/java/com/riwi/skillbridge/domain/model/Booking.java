package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Booking(
        UUID id,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        BookingStatus status,
        long version
) {
    public Booking cancel() {
        if (status == BookingStatus.CANCELLED) {
            return this;
        }
        if (status == BookingStatus.COMPLETED) {
            throw new BusinessRuleException("No se pueden cancelar reservas completadas");
        }
        return new Booking(id, offeringId, customerId, scheduledAt, BookingStatus.CANCELLED, version);
    }

    /**
     * Determina si la ventana de pago ha expirado.
     * Una reserva en estado CREATED no puede pagarse si faltan menos de 12 horas para la sesión o si la fecha ya pasó.
     */
    public boolean isPaymentWindowExpired(Instant now) {
        return status == BookingStatus.CREATED && scheduledAt.isBefore(now.plus(Duration.ofHours(12)));
    }
}
