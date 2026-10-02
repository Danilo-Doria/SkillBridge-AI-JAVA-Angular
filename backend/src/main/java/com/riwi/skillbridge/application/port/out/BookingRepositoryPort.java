package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

import java.util.List;
import java.util.UUID;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    // Nuevo puerto de salida para el listado de reservas por is de usuario
    List<Booking> findByCustomerId(UUID customerId);
}
