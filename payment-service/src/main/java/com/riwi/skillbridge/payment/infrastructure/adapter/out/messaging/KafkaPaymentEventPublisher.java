package com.riwi.skillbridge.payment.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.payment.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.payment.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.payment.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.payment.domain.event.PaymentApproved;
import com.riwi.skillbridge.payment.domain.event.PaymentRejected;
import com.riwi.skillbridge.payment.domain.exception.MessagePublishException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica {@code PaymentApproved}/{@code PaymentRejected} en {@code payment-events} (clave = bookingId), con el mismo
 * envelope, eventType, aggregateType y cabeceras que {@code KafkaPaymentEventPublisher} del monolito.
 * Espera el ack del broker; si no llega lanza {@link MessagePublishException} (el webhook responde 503 y Stripe
 * reintenta).
 */
@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);
    static final String AGGREGATE_TYPE = "Payment";

    private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final String topic;
    private final String typeId;
    private final Duration publishTimeout;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate,
                                      MeterRegistry meterRegistry,
                                      Clock clock,
                                      @Value("${app.kafka.topic.payment-events:payment-events}") String topic,
                                      @Value("${app.kafka.type-id-header:com.riwi.skillbridge.application.port.out.event.BusinessEvent}") String typeId,
                                      @Value("${app.kafka.publish-timeout:10s}") Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.topic = topic;
        this.typeId = typeId;
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void publishApproved(PaymentApproved event) {
        UUID eventId = event.eventId() != null ? event.eventId() : UUID.randomUUID();
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : clock.instant();
        String correlationId = event.correlationId() != null ? event.correlationId() : CorrelationIdHolder.currentOrNew();
        send(new BusinessEvent<>(eventId, "PaymentApproved", event.bookingId().toString(), AGGREGATE_TYPE,
            occurredAt, correlationId, event.version(), event, null, null, null, null, null, null));
    }

    @Override
    public void publishRejected(PaymentRejected event, UUID eventId) {
        send(new BusinessEvent<>(eventId != null ? eventId : UUID.randomUUID(), "PaymentRejected",
            event.bookingId().toString(), AGGREGATE_TYPE, clock.instant(), CorrelationIdHolder.currentOrNew(), 1, event,
            null, null, null, null, null, null));
    }

    private void send(BusinessEvent<?> envelope) {
        try {
            kafkaTemplate.send(KafkaEventHeaders.record(topic, envelope, typeId))
                .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Published eventId={} eventType={} bookingId={} topic={} correlationId={}",
                envelope.eventId(), envelope.eventType(), envelope.aggregateId(), topic, envelope.correlationId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(envelope, e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw failure(envelope, e);
        }
    }

    private MessagePublishException failure(BusinessEvent<?> envelope, Exception cause) {
        meterRegistry.counter("skillbridge.messaging.publish.failures",
            "topic", topic, "eventType", envelope.eventType()).increment();
        log.error("Could not publish eventId={} eventType={} bookingId={} topic={}",
            envelope.eventId(), envelope.eventType(), envelope.aggregateId(), topic, cause);
        return new MessagePublishException("No se pudo publicar " + envelope.eventType()
            + " para la reserva " + envelope.aggregateId(), cause);
    }
}
