package com.riwi.skillbridge.application.port.in;

/** Publica el estado de las reservas abiertas (CREATED/CONFIRMED) como {@code BookingStateSnapshot}. */
public interface PublishBookingSnapshotUseCase {
    /** @return número de reservas publicadas */
    int publishOpenBookingsSnapshot();
}
