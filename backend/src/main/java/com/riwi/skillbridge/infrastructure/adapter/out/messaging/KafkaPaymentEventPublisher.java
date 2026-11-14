package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica {@code PaymentApproved}/{@code PaymentRejected} en {@code payment-events} (clave = bookingId)
 * dentro del envelope {@link BusinessEvent}. Sustituye a el antiguo {@code RabbitPaymentEventPublisher} (retirado en HU-104) tras el mismo puerto.
 *
 * <p>Espera el ack del broker: si no llega, lanza {@link MessagePublishException} para que el llamante
 * (p. ej. el webhook de Stripe, que reintentará) no dé el pago por notificado. Un pago no se pierde en silencio.
 */
@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);
    static final String AGGREGATE_TYPE = "Payment";

    private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final String topic;
    private final Duration publishTimeout;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate,
                                      MeterRegistry meterRegistry,
                                      @Value("${app.kafka.topic.payment-events:payment-events}") String topic,
                                      @Value("${app.messaging.kafka.publish-timeout:10s}") Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void publishApproved(PaymentApproved event) {
        UUID eventId = event.eventId() != null ? event.eventId() : UUID.randomUUID();
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        send(new BusinessEvent<>(eventId, "PaymentApproved", event.bookingId().toString(), AGGREGATE_TYPE,
            occurredAt, KafkaEventHeaders.currentCorrelationId(event.correlationId()), event.version(), event,
            null, null, null, null, null, null));
    }

    @Override
    public void publishRejected(PaymentRejected event) {
        send(new BusinessEvent<>(event.eventId() != null ? event.eventId() : UUID.randomUUID(), "PaymentRejected", event.bookingId().toString(), AGGREGATE_TYPE,
            Instant.now(), KafkaEventHeaders.currentCorrelationId(null), 1, event,
            null, null, null, null, null, null));
    }

    private void send(BusinessEvent<?> envelope) {
        try {
            kafkaTemplate.send(KafkaEventHeaders.record(topic, envelope))
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
