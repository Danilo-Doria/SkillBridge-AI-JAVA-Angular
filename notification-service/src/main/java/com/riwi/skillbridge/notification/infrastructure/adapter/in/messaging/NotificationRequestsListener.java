package com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.notification.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.idempotency.IdempotentEventProcessor;
import com.riwi.skillbridge.notification.infrastructure.config.KafkaConsumerConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code notification-requests} con group-id propio ({@code notification-service}, distinto del
 * monolito) y delega en {@link ProcessNotificationUseCase}. Idempotente por {@code eventId}.
 *
 * <p>Errores deterministas ({@code IllegalArgumentException}, {@code PermanentNotificationException}) van
 * directos a {@code notification-requests.DLT}; el resto (p. ej. SMTP caído) se reintenta con backoff.
 */
@Component
public class NotificationRequestsListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRequestsListener.class);

    private final ProcessNotificationUseCase processNotification;
    private final IdempotentEventProcessor idempotentProcessor;
    private final ObjectMapper objectMapper;
    private final String consumerGroup;

    public NotificationRequestsListener(ProcessNotificationUseCase processNotification,
                                        IdempotentEventProcessor idempotentProcessor,
                                        ObjectMapper objectMapper,
                                        @Value("${notification.kafka.group-id:notification-service}") String consumerGroup) {
        this.processNotification = processNotification;
        this.idempotentProcessor = idempotentProcessor;
        this.objectMapper = objectMapper;
        this.consumerGroup = consumerGroup;
    }

    @KafkaListener(
        id = "notification-requests-listener",
        topics = "${notification.kafka.topic:notification-requests}",
        groupId = "${notification.kafka.group-id:notification-service}",
        containerFactory = KafkaConsumerConfiguration.LISTENER_FACTORY)
    public void onNotification(ConsumerRecord<String, BusinessEventEnvelope> record) {
        BusinessEventEnvelope event = record.value();
        if (event == null) {
            log.warn("Ignoring tombstone on topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());
            return;
        }
        try (KafkaCorrelationScope scope = KafkaCorrelationScope.open(record)) {
            if (event.eventId() == null) {
                throw new IllegalArgumentException("Envelope sin eventId en offset " + record.offset());
            }
            if (!KafkaEventHeaders.NOTIFICATION_EVENT_TYPES.contains(event.eventType())) {
                throw new IllegalArgumentException("eventType no soportado: " + event.eventType());
            }
            if (event.payload() == null || event.payload().isNull()) {
                throw new IllegalArgumentException("Evento " + event.eventId() + " sin payload");
            }
            NotificationRequest request = toRequest(event);
            log.info("Consumed eventId={} eventType={} bookingId={} partition={} offset={}",
                event.eventId(), event.eventType(), request.bookingId(), record.partition(), record.offset());
            idempotentProcessor.processOnce(consumerGroup, event.eventId(), event.eventType(),
                () -> processNotification.process(request));
        }
    }

    private NotificationRequest toRequest(BusinessEventEnvelope event) {
        try {
            return objectMapper.treeToValue(event.payload(), NotificationPayload.class).toDomain();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload ilegible en evento " + event.eventId(), e);
        }
    }
}
