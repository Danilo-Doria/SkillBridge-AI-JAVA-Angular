package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.infrastructure.adapter.in.messaging.idempotency.IdempotentEventProcessor;
import com.riwi.skillbridge.infrastructure.config.KafkaMessagingConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code notification-requests} y delega en {@link ProcessNotificationUseCase}.
 * Sustituye a el antiguo {@code NotificationConsumer} (RabbitMQ, retirado en HU-104). Idempotente por {@code eventId}.
 *
 * <p>{@code PermanentNotificationException} va directa a {@code notification-requests.DLT} (sin reintentos),
 * igual que el {@code AmqpRejectAndDontRequeueException} anterior; el resto de errores se reintenta con backoff.
 */
@Component
// HU-104: con notification-service desplegado, poner a false para no enviar dos veces (otro group-id).
@ConditionalOnProperty(name = "app.messaging.kafka.local-notification-consumer", havingValue = "true", matchIfMissing = true)
public class KafkaNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationConsumer.class);

    private final ProcessNotificationUseCase processNotification;
    private final IdempotentEventProcessor idempotentProcessor;
    private final ObjectMapper objectMapper;
    private final String consumerGroup;

    public KafkaNotificationConsumer(ProcessNotificationUseCase processNotification,
                                     IdempotentEventProcessor idempotentProcessor,
                                     ObjectMapper objectMapper,
                                     @Value("${app.messaging.kafka.consumer-groups.notifications:skillbridge-notifications}")
                                     String consumerGroup) {
        this.processNotification = processNotification;
        this.idempotentProcessor = idempotentProcessor;
        this.objectMapper = objectMapper;
        this.consumerGroup = consumerGroup;
    }

    @KafkaListener(
        topics = "${app.kafka.topic.notification-requests:notification-requests}",
        groupId = "${app.messaging.kafka.consumer-groups.notifications:skillbridge-notifications}",
        containerFactory = KafkaMessagingConfiguration.LISTENER_FACTORY)
    public void onNotification(ConsumerRecord<String, BusinessEvent<?>> record) {
        BusinessEvent<?> event = record.value();
        if (event == null) {
            log.warn("Ignoring tombstone on topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());
            return;
        }
        try (KafkaCorrelationScope scope = KafkaCorrelationScope.open(record)) {
            if (event.payload() == null) {
                throw new IllegalArgumentException("Evento " + event.eventId() + " sin payload");
            }
            NotificationMessage message = objectMapper.convertValue(event.payload(), NotificationMessage.class);
            log.info("Consumed eventId={} eventType={} bookingId={} partition={} offset={}",
                event.eventId(), event.eventType(), message.bookingId(), record.partition(), record.offset());
            idempotentProcessor.processOnce(consumerGroup, event.eventId(), event.eventType(),
                () -> processNotification.process(message));
        }
    }
}
