package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

public interface NotificationPublisherPort {
    // Publica una notificación de forma asíncrona
    void publish(NotificationMessage message);
}
