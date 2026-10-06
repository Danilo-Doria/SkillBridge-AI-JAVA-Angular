package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

public interface NotificationPublisherPort {
    void bookingCreated(Booking booking);
}
