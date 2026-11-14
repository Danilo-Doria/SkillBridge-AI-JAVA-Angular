package com.riwi.skillbridge.payment.application.port.in;

import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.OfferingView;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyección de {@code booking-events} sobre los read models del schema {@code payment}.
 * Idempotente: un {@code eventId} ya aplicado o un evento más antiguo que el último aplicado no cambia nada.
 *
 * @return {@code true} si el read model cambió
 */
public interface SyncReadModelUseCase {

    boolean bookingCreated(BookingView booking, UUID eventId, Instant occurredAt);

    /**
     * {@code BookingStatusChanged} / {@code BookingStateSnapshot}: upsert completo con el estado textual del monolito.
     * Un evento más antiguo que el último aplicado no pisa el estado más nuevo.
     */
    boolean bookingStateChanged(BookingView booking, UUID eventId, Instant occurredAt);

    boolean bookingCancelled(UUID bookingId, UUID offeringId, UUID customerId, UUID eventId, Instant occurredAt);

    boolean offeringUpserted(OfferingView offering, UUID eventId, Instant occurredAt);
}
