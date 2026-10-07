package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepositoryPort {
    Optional<Booking> findById(UUID id);
    Booking save(Booking booking);
    List<Booking> findByCustomerEmail(String email);
}
