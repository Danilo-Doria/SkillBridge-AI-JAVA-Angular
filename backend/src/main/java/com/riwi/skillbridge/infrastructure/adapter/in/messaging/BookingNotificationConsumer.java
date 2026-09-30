package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.infrastructure.adapter.out.messaging.BookingCreatedEvent;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);

    @RabbitListener(queues = RabbitConfiguration.BOOKING_CREATED_QUEUE)
    public void onBookingCreated(BookingCreatedEvent event) {
        // Extension point: email, WhatsApp, push notification, audit, another microservice, etc.
        log.info("Async booking notification -> bookingId={}, scheduledAt={}", event.bookingId(), event.scheduledAt());
    }
}
