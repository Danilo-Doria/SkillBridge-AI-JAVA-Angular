package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaAuditConsumer {

    @KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(BusinessEvent<?> event) {
        log.info("🔔 [AUDIT KAFKA CONSUMER] Evento de negocio recibido:");
        log.info("   -> Event ID: {}", event.eventId());
        log.info("   -> Event Type: {}", event.eventType());
        log.info("   -> Aggregate ID: {}", event.aggregateId());
        log.info("   -> Correlation ID: {}", event.correlationId());
        log.info("   -> Payload: {}", event.payload());
    }
}
