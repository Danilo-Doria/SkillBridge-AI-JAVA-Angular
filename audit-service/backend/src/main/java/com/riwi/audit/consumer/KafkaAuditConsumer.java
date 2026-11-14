package com.riwi.audit.consumer;

import com.riwi.audit.model.BusinessEvent;
import com.riwi.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAuditConsumer {

    static final java.util.Set<String> NON_AUDITABLE_EVENT_TYPES = java.util.Set.of("OfferingUpserted", "BookingStateSnapshot");

    private final AuditEventRepository auditEventRepository;

    @KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(BusinessEvent event) {
        log.info("[AUDIT SERVICE] Event received - eventId={}, eventType={}, aggregateId={}, correlationId={}",
                event.getEventId(), event.getEventType(), event.getAggregateId(), event.getCorrelationId());
        
        // HU-104: OfferingUpserted y BookingStateSnapshot son eventos de read model (ECST), no hechos auditables: el alta/cambio ya
        // queda auditado por OfferingCreated/Updated/... y el snapshot de arranque lo emitiría en cada reinicio.
        if (NON_AUDITABLE_EVENT_TYPES.contains(event.getEventType())) {
            log.debug("[AUDIT SERVICE] Skipping read-model event eventType={}", event.getEventType());
            return;
        }
        auditEventRepository.save(event);
    }
}
