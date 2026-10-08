package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    Optional<Booking> findById(UUID bookingId);

    default Optional<Booking> findByIdForCancellation(UUID bookingId) {
        return findById(bookingId);
    }

    List<Booking> findByCustomerEmail(String email);
}
