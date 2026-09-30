package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
}
