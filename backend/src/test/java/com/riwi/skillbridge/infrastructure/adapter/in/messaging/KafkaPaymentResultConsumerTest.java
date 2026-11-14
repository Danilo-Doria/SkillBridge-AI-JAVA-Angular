package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.in.PaymentResultUseCase;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.infrastructure.adapter.in.messaging.idempotency.IdempotentEventProcessor;
import com.riwi.skillbridge.application.port.out.ProcessedEventStorePort;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaEnvelopeWire;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaEventHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class KafkaPaymentResultConsumerTest {

    private static final String TOPIC = "payment-events";
    private static final String GROUP = "skillbridge-booking-payments";

    private final PaymentResultUseCase useCase = mock(PaymentResultUseCase.class);
    private final InMemoryProcessedEvents store = new InMemoryProcessedEvents();
    private final KafkaPaymentResultConsumer consumer = new KafkaPaymentResultConsumer(useCase,
        new IdempotentEventProcessor(store, store.rollingBackTransactions()),
        KafkaEnvelopeWire.BOOT_LIKE_MAPPER, GROUP);

    @AfterEach
    void clear() {
        CorrelationIdHolder.clear();
        MDC.clear();
    }

    @Test
    void approvedEventConfirmsBookingAndDuplicateHasSingleEffect() {
        UUID bookingId = UUID.randomUUID();
        BusinessEvent<?> event = approved(bookingId, "corr-1");

        consumer.onPaymentEvent(record(event, "corr-1"));
        consumer.onPaymentEvent(record(event, "corr-1"));

        verify(useCase, times(1)).processPaymentApproved(bookingId);
        assertThat(store.marked).containsExactly(GROUP + ":" + event.eventId());
    }

    @Test
    void restoresCorrelationFromHeaderInMdcAndHolderAndCleansUp() {
        UUID bookingId = UUID.randomUUID();
        AtomicReference<String> holderSeen = new AtomicReference<>();
        AtomicReference<String> mdcSeen = new AtomicReference<>();
        doAnswer(inv -> {
            holderSeen.set(CorrelationIdHolder.get());
            mdcSeen.set(MDC.get("correlationId"));
            return null;
        }).when(useCase).processPaymentApproved(bookingId);

        consumer.onPaymentEvent(record(approved(bookingId, "corr-envelope"), "corr-header"));

        assertThat(holderSeen.get()).isEqualTo("corr-header");
        assertThat(mdcSeen.get()).isEqualTo("corr-header");
        assertThat(CorrelationIdHolder.get()).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void fallsBackToEnvelopeCorrelationWithoutHeader() {
        UUID bookingId = UUID.randomUUID();
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(MDC.get("correlationId"));
            return null;
        }).when(useCase).processPaymentApproved(bookingId);

        consumer.onPaymentEvent(record(approved(bookingId, "corr-envelope"), null));

        assertThat(seen.get()).isEqualTo("corr-envelope");
    }

    @Test
    void rejectedEventDelegatesReason() {
        UUID bookingId = UUID.randomUUID();
        BusinessEvent<PaymentRejected> event = new BusinessEvent<>(UUID.randomUUID(), "PaymentRejected", bookingId.toString(),
            "Payment", Instant.now(), "c", 1, new PaymentRejected(bookingId, "declined"),
            null, null, null, null, null, null);

        consumer.onPaymentEvent(record(event, "c"));

        verify(useCase).processPaymentRejected(bookingId, "declined");
    }

    @Test
    void failedEffectRollsBackMarkSoRetryCanProcess() {
        UUID bookingId = UUID.randomUUID();
        BusinessEvent<?> event = approved(bookingId, "c");
        doThrow(new BusinessRuleException("La reserva no esta en estado CREATED"))
            .doNothing().when(useCase).processPaymentApproved(bookingId);

        assertThatThrownBy(() -> consumer.onPaymentEvent(record(event, "c"))).isInstanceOf(BusinessRuleException.class);
        consumer.onPaymentEvent(record(event, "c"));

        verify(useCase, times(2)).processPaymentApproved(bookingId);
    }

    @Test
    void unknownEventTypeIsIgnoredAndMissingBookingIdIsRejected() {
        BusinessEvent<PaymentRejected> unknown = new BusinessEvent<>(UUID.randomUUID(), "PaymentRefunded", "agg",
            "Payment", Instant.now(), "c", 1, new PaymentRejected(UUID.randomUUID(), "x"),
            null, null, null, null, null, null);
        consumer.onPaymentEvent(record(unknown, "c"));
        verify(useCase, never()).processPaymentApproved(any());

        BusinessEvent<PaymentRejected> noBooking = new BusinessEvent<>(UUID.randomUUID(), "PaymentRejected", "agg",
            "Payment", Instant.now(), "c", 1, new PaymentRejected(null, "x"), null, null, null, null, null, null);
        assertThatThrownBy(() -> consumer.onPaymentEvent(record(noBooking, "c")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static BusinessEvent<?> approved(UUID bookingId, String correlationId) {
        PaymentApproved payload = new PaymentApproved(bookingId);
        return new BusinessEvent<>(UUID.randomUUID(), "PaymentApproved", bookingId.toString(), "Payment",
            Instant.now(), correlationId, 1, payload, null, null, null, null, null, null);
    }

    /** Pasa el envelope por el cable real (JSON) para que el payload llegue como Map, igual que en producción. */
    private static ConsumerRecord<String, BusinessEvent<?>> record(BusinessEvent<?> event, String headerCorrelation) {
        ConsumerRecord<String, BusinessEvent<?>> record =
            new ConsumerRecord<>(TOPIC, 0, 0L, event.aggregateId(), KafkaEnvelopeWire.roundTrip(TOPIC, event));
        if (headerCorrelation != null) {
            record.headers().add(KafkaEventHeaders.CORRELATION_ID, headerCorrelation.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    /** Simula processed_events y el rollback de la transacción si el efecto falla. */
    static final class InMemoryProcessedEvents implements ProcessedEventStorePort {
        final Set<String> marked = new HashSet<>();

        TransactionOperations rollingBackTransactions() {
            return new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    Set<String> snapshot = new HashSet<>(marked);
                    try {
                        return action.doInTransaction(null);
                    } catch (RuntimeException e) {
                        marked.retainAll(snapshot);
                        throw e;
                    }
                }
            };
        }

        @Override
        public boolean markProcessed(String consumerGroup, UUID eventId, String eventType) {
            return marked.add(consumerGroup + ":" + eventId);
        }
    }
}
