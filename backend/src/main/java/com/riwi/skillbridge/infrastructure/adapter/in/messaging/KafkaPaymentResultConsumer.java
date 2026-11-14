package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.in.PaymentResultUseCase;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.infrastructure.adapter.in.messaging.idempotency.IdempotentEventProcessor;
import com.riwi.skillbridge.infrastructure.config.KafkaMessagingConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consume {@code payment-events} y actualiza la reserva vía {@link PaymentResultUseCase}.
 * Sustituye a el antiguo {@code PaymentResultConsumer} (RabbitMQ, retirado en HU-104).
 *
 * <p>Idempotente por {@code eventId}. A diferencia del consumidor AMQP, los errores NO se tragan:
 * se propagan al {@code DefaultErrorHandler} (reintentos con backoff y luego {@code payment-events.DLT}).
 * Reglas de negocio violadas (p. ej. la reserva ya no está en CREATED) van a la DLT sin reintentos.
 */
@Component
public class KafkaPaymentResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentResultConsumer.class);

    private final PaymentResultUseCase paymentResultUseCase;
    private final IdempotentEventProcessor idempotentProcessor;
    private final ObjectMapper objectMapper;
    private final String consumerGroup;

    public KafkaPaymentResultConsumer(PaymentResultUseCase paymentResultUseCase,
                                      IdempotentEventProcessor idempotentProcessor,
                                      ObjectMapper objectMapper,
                                      @Value("${app.messaging.kafka.consumer-groups.payments:skillbridge-booking-payments}")
                                      String consumerGroup) {
        this.paymentResultUseCase = paymentResultUseCase;
        this.idempotentProcessor = idempotentProcessor;
        this.objectMapper = objectMapper;
        this.consumerGroup = consumerGroup;
    }

    @KafkaListener(
        topics = "${app.kafka.topic.payment-events:payment-events}",
        groupId = "${app.messaging.kafka.consumer-groups.payments:skillbridge-booking-payments}",
        containerFactory = KafkaMessagingConfiguration.LISTENER_FACTORY)
    public void onPaymentEvent(ConsumerRecord<String, BusinessEvent<?>> record) {
        BusinessEvent<?> event = record.value();
        if (event == null) {
            log.warn("Ignoring tombstone on topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());
            return;
        }
        try (KafkaCorrelationScope scope = KafkaCorrelationScope.open(record)) {
            log.info("Consumed eventId={} eventType={} bookingId={} partition={} offset={}",
                event.eventId(), event.eventType(), event.aggregateId(), record.partition(), record.offset());
            Runnable effect = switch (event.eventType()) {
                case "PaymentApproved" -> {
                    PaymentApproved approved = payload(event, PaymentApproved.class);
                    UUID bookingId = requireBookingId(approved.bookingId(), event);
                    yield () -> paymentResultUseCase.processPaymentApproved(bookingId);
                }
                case "PaymentRejected" -> {
                    PaymentRejected rejected = payload(event, PaymentRejected.class);
                    UUID bookingId = requireBookingId(rejected.bookingId(), event);
                    yield () -> paymentResultUseCase.processPaymentRejected(bookingId, rejected.reason());
                }
                default -> null;
            };
            if (effect == null) {
                log.debug("eventType={} not handled by booking consumer, skipping eventId={}",
                    event.eventType(), event.eventId());
                return;
            }
            idempotentProcessor.processOnce(consumerGroup, event.eventId(), event.eventType(), effect);
        }
    }

    private static UUID requireBookingId(UUID bookingId, BusinessEvent<?> event) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Evento " + event.eventId() + " sin bookingId");
        }
        return bookingId;
    }

    private <T> T payload(BusinessEvent<?> event, Class<T> type) {
        if (event.payload() == null) {
            throw new IllegalArgumentException("Evento " + event.eventId() + " sin payload");
        }
        T value = objectMapper.convertValue(event.payload(), type);
        if (value == null) {
            throw new IllegalArgumentException("Payload ilegible en evento " + event.eventId());
        }
        return value;
    }
}
