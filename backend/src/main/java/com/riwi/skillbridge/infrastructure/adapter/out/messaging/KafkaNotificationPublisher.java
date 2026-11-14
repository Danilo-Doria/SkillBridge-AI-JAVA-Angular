package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica solicitudes de notificación en {@code notification-requests} (clave = bookingId).
 * Sustituye a el antiguo {@code RabbitNotificationPublisher} (retirado en HU-104) tras el mismo puerto.
 *
 * <p>Igual que antes, un fallo del broker no rompe la reserva ya guardada: se registra y se cuenta en
 * {@code skillbridge.messaging.publish.failures} (equivalente Kafka del "mensaje no enrutable" de RabbitMQ).
 */
@Component
public class KafkaNotificationPublisher implements NotificationPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationPublisher.class);
    static final String AGGREGATE_TYPE = "Booking";

    private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final String topic;
    private final UserRepositoryPort users;

    /** Sin resolución de destinatario (tests de cable). */
    public KafkaNotificationPublisher(KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate,
                                      MeterRegistry meterRegistry, String topic) {
        this(kafkaTemplate, meterRegistry, topic, null);
    }

    @Autowired
    public KafkaNotificationPublisher(KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate,
                                      MeterRegistry meterRegistry,
                                      @Value("${app.kafka.topic.notification-requests:notification-requests}") String topic,
                                      UserRepositoryPort users) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.topic = topic;
        this.users = users;
    }

    @Override
    public void publish(NotificationMessage message) {
        String email = message.recipientEmail();
        String name = message.recipientName();
        if (email == null) {
            UserAccount recipient = resolveRecipient(message);
            email = recipient == null ? null : recipient.email();
            name = recipient == null ? null : recipient.name();
        }
        NotificationRequestPayload payload = NotificationRequestPayload.of(message, email, name);
        BusinessEvent<NotificationRequestPayload> envelope = new BusinessEvent<>(message.eventId(),
            eventTypeFor(message.notificationType()), message.bookingId().toString(), AGGREGATE_TYPE,
            message.occurredAt(), KafkaEventHeaders.currentCorrelationId(null), 1, payload,
            null, null, null, null, null, null);
        // HU-104: dentro de una transacción (BookingService.create/cancel) se envía tras el COMMIT, para no
        // notificar una reserva que luego se revierte. Sin transacción activa, se envía en el acto.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(envelope, message);
                }
            });
        } else {
            send(envelope, message);
        }
    }

    private void send(BusinessEvent<NotificationRequestPayload> envelope, NotificationMessage message) {
        try {
            kafkaTemplate.send(KafkaEventHeaders.record(topic, envelope))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Published eventId={} eventType={} bookingId={} topic={} correlationId={}",
                            envelope.eventId(), envelope.eventType(), message.bookingId(), topic,
                            envelope.correlationId());
                    } else {
                        recordFailure(envelope, ex);
                    }
                });
        } catch (RuntimeException e) {
            // La reserva ya fue guardada: un fallo del broker no debe romperla
            recordFailure(envelope, e);
        }
    }

    /** El destinatario es opcional en el contrato: si no se puede resolver, se publica igual (el consumidor decide). */
    private UserAccount resolveRecipient(NotificationMessage message) {
        if (users == null) {
            return null;
        }
        try {
            return users.findById(message.userId()).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Could not resolve recipient for userId={} bookingId={}", message.userId(), message.bookingId(), e);
            return null;
        }
    }

    public static String eventTypeFor(NotificationType notificationType) {
        return switch (notificationType) {
            case BOOKING_CREATED -> "BookingCreatedNotification";
            case BOOKING_CANCELLED -> "BookingCancelledNotification";
            case PAYMENT_REMINDER -> "PaymentReminderNotification";
        };
    }

    private void recordFailure(BusinessEvent<?> envelope, Throwable cause) {
        meterRegistry.counter("skillbridge.messaging.publish.failures",
            "topic", topic, "eventType", envelope.eventType()).increment();
        log.error("Could not publish notification eventId={} bookingId={} topic={}",
            envelope.eventId(), envelope.aggregateId(), topic, cause);
    }
}
