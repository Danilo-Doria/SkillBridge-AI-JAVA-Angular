package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaNotificationPublisherTest {

    private static final String TOPIC = "notification-requests";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, BusinessEvent<?>> template = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final KafkaNotificationPublisher publisher = new KafkaNotificationPublisher(template, meters, TOPIC);

    @AfterEach
    void clear() {
        CorrelationIdHolder.clear();
    }

    @Test
    void publishesEachNotificationTypeKeyedByBookingWithEnvelopeAndHeaders() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        CorrelationIdHolder.set("corr-booking");
        NotificationMessage message = NotificationMessage.paymentReminder(UUID.randomUUID(), UUID.randomUUID());

        publisher.publish(message);

        ProducerRecord<String, BusinessEvent<?>> record = captured();
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo(message.bookingId().toString());
        assertThat(record.value().eventId()).isEqualTo(message.eventId());
        assertThat(record.value().eventType()).isEqualTo("PaymentReminderNotification");
        assertThat(record.value().correlationId()).isEqualTo("corr-booking");
        assertThat(KafkaEventHeaders.read(record.headers(), KafkaEventHeaders.CORRELATION_ID)).isEqualTo("corr-booking");

        // El payload sobrevive al cable y se reconstruye como NotificationMessage.
        BusinessEvent<?> read = KafkaEnvelopeWire.roundTrip(TOPIC, record.value());
        NotificationMessage back = KafkaEnvelopeWire.BOOT_LIKE_MAPPER.convertValue(read.payload(), NotificationMessage.class);
        assertThat(back.eventId()).isEqualTo(message.eventId());
        assertThat(back.bookingId()).isEqualTo(message.bookingId());
        assertThat(back.userId()).isEqualTo(message.userId());
        assertThat(back.notificationType()).isEqualTo(message.notificationType());
        // El envelope pasa el payload por un Map (Instant como número decimal): precisión de microsegundos.
        assertThat(java.time.Duration.between(message.occurredAt(), back.occurredAt()).abs().toMillis()).isZero();
    }

    @Test
    void eventTypesFollowContract() {
        assertThat(KafkaNotificationPublisher.eventTypeFor(com.riwi.skillbridge.application.port.out.NotificationType.BOOKING_CREATED))
            .isEqualTo("BookingCreatedNotification");
        assertThat(KafkaNotificationPublisher.eventTypeFor(com.riwi.skillbridge.application.port.out.NotificationType.BOOKING_CANCELLED))
            .isEqualTo("BookingCancelledNotification");
    }

    @Test
    void brokerFailureDoesNotBreakBookingButIsCounted() {
        when(template.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("metadata timeout"));

        assertThatCode(() -> publisher.publish(NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID())))
            .doesNotThrowAnyException();
        assertThat(meters.counter("skillbridge.messaging.publish.failures",
            "topic", TOPIC, "eventType", "BookingCreatedNotification").count()).isEqualTo(1.0);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, BusinessEvent<?>> captured() {
        ArgumentCaptor<ProducerRecord<String, BusinessEvent<?>>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }
}
