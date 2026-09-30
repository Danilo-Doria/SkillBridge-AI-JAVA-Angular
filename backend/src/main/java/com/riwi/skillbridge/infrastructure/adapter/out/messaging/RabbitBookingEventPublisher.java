package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class RabbitBookingEventPublisher implements BookingEventPublisherPort {
    private final RabbitTemplate rabbitTemplate;

    public RabbitBookingEventPublisher(RabbitTemplate rabbitTemplate) { this.rabbitTemplate = rabbitTemplate; }

    @Override
    public void bookingCreated(Booking booking) {
        BookingCreatedEvent event = new BookingCreatedEvent(
                booking.id(), booking.offeringId(), booking.customerId(), booking.scheduledAt(), Instant.now());
        rabbitTemplate.convertAndSend(
                RabbitConfiguration.BOOKING_EXCHANGE,
                RabbitConfiguration.BOOKING_CREATED_KEY,
                event);
    }
}
