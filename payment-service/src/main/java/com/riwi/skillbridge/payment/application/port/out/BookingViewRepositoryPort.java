package com.riwi.skillbridge.payment.application.port.out;

import com.riwi.skillbridge.payment.domain.model.BookingView;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BookingViewRepositoryPort {

    Optional<BookingView> findById(UUID bookingId);

    /**
     * Upsert completo condicionado a que {@code occurredAt} sea posterior al último evento aplicado. Lo usan
     * {@code BookingCreated}, {@code BookingStatusChanged} y {@code BookingStateSnapshot}.
     */
    boolean upsertCreated(BookingView booking, UUID eventId, Instant occurredAt);

    /** Marca la reserva como CANCELLED (o la crea así si aún no existía), con la misma condición temporal. */
    boolean applyCancelled(UUID bookingId, UUID offeringId, UUID customerId, UUID eventId, Instant occurredAt);
}
