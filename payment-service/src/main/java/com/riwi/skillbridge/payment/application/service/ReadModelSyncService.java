package com.riwi.skillbridge.payment.application.service;

import com.riwi.skillbridge.payment.application.port.in.SyncReadModelUseCase;
import com.riwi.skillbridge.payment.application.port.out.BookingViewRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.OfferingViewRepositoryPort;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReadModelSyncService implements SyncReadModelUseCase {

    private final BookingViewRepositoryPort bookings;
    private final OfferingViewRepositoryPort offerings;

    public ReadModelSyncService(BookingViewRepositoryPort bookings, OfferingViewRepositoryPort offerings) {
        this.bookings = bookings;
        this.offerings = offerings;
    }

    @Override
    public boolean bookingCreated(BookingView booking, UUID eventId, Instant occurredAt) {
        requireIds(booking.bookingId(), booking.customerId(), eventId, occurredAt);
        return bookings.upsertCreated(booking, eventId, occurredAt);
    }

    @Override
    public boolean bookingStateChanged(BookingView booking, UUID eventId, Instant occurredAt) {
        requireIds(booking.bookingId(), booking.customerId(), eventId, occurredAt);
        if (booking.status() == null || booking.status().isBlank()) {
            throw new IllegalArgumentException("Evento de estado de reserva sin status");
        }
        return bookings.upsertCreated(booking, eventId, occurredAt);
    }

    @Override
    public boolean bookingCancelled(UUID bookingId, UUID offeringId, UUID customerId, UUID eventId, Instant occurredAt) {
        requireIds(bookingId, customerId, eventId, occurredAt);
        return bookings.applyCancelled(bookingId, offeringId, customerId, eventId, occurredAt);
    }

    @Override
    public boolean offeringUpserted(OfferingView offering, UUID eventId, Instant occurredAt) {
        Objects.requireNonNull(offering.id(), "offering id");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        return offerings.upsert(offering, eventId, occurredAt);
    }

    private static void requireIds(UUID bookingId, UUID customerId, UUID eventId, Instant occurredAt) {
        if (bookingId == null || customerId == null || eventId == null || occurredAt == null) {
            throw new IllegalArgumentException("Evento de reserva incompleto (bookingId, customerId, eventId, occurredAt)");
        }
    }
}
