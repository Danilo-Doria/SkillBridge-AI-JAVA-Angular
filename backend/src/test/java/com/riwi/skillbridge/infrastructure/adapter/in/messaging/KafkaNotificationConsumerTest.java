package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.PermanentNotificationException;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.infrastructure.adapter.in.messaging.idempotency.IdempotentEventProcessor;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaEnvelopeWire;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaEventHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class KafkaNotificationConsumerTest {

    private static final String TOPIC = "notification-requests";
    private static final String GROUP = "skillbridge-notifications";

    private final ProcessNotificationUseCase useCase = mock(ProcessNotificationUseCase.class);
    private final KafkaPaymentResultConsumerTest.InMemoryProcessedEvents store =
        new KafkaPaymentResultConsumerTest.InMemoryProcessedEvents();
    private final KafkaNotificationConsumer consumer = new KafkaNotificationConsumer(useCase,
        new IdempotentEventProcessor(store, store.rollingBackTransactions()), KafkaEnvelopeWire.BOOT_LIKE_MAPPER, GROUP);

    @AfterEach
    void clear() {
        CorrelationIdHolder.clear();
        MDC.clear();
    }

    @Test
    void delegatesOnceForDuplicatesWithCorrelationRestored() {
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());
        AtomicReference<String> mdcSeen = new AtomicReference<>();
        doAnswer(inv -> {
            mdcSeen.set(MDC.get("correlationId"));
            return null;
        }).when(useCase).process(any());
        ConsumerRecord<String, BusinessEvent<?>> record = record(message, "corr-n");

        consumer.onNotification(record);
        consumer.onNotification(record);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(useCase, times(1)).process(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(message.eventId());
        assertThat(captor.getValue().bookingId()).isEqualTo(message.bookingId());
        assertThat(mdcSeen.get()).isEqualTo("corr-n");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void permanentFailurePropagatesToErrorHandlerAndAllowsReprocessing() {
        NotificationMessage message = NotificationMessage.bookingCancelled(UUID.randomUUID(), UUID.randomUUID());
        doThrow(new PermanentNotificationException("bad address")).doNothing().when(useCase).process(any());

        assertThatThrownBy(() -> consumer.onNotification(record(message, "c")))
            .isInstanceOf(PermanentNotificationException.class);
        // Reinyectado desde la DLT (mismo eventId): la marca se revirtió, así que se procesa.
        consumer.onNotification(record(message, "c"));

        verify(useCase, times(2)).process(any());
    }

    private static ConsumerRecord<String, BusinessEvent<?>> record(NotificationMessage message, String correlation) {
        BusinessEvent<NotificationMessage> event = new BusinessEvent<>(message.eventId(), "BookingCreatedNotification",
            message.bookingId().toString(), "Booking", message.occurredAt(), correlation, 1, message,
            null, null, null, null, null, null);
        ConsumerRecord<String, BusinessEvent<?>> record =
            new ConsumerRecord<>(TOPIC, 0, 0L, event.aggregateId(), KafkaEnvelopeWire.roundTrip(TOPIC, event));
        record.headers().add(KafkaEventHeaders.CORRELATION_ID, correlation.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
