package com.riwi.audit.consumer;

import com.riwi.audit.model.BusinessEvent;
import com.riwi.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-104: los eventos de read model (OfferingUpserted) no se guardan como auditoría. */
class KafkaAuditConsumerTest {

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final KafkaAuditConsumer consumer = new KafkaAuditConsumer(repository);

    private BusinessEvent event(String type) {
        BusinessEvent e = new BusinessEvent();
        e.setEventId(UUID.randomUUID());
        e.setEventType(type);
        e.setAggregateId(UUID.randomUUID().toString());
        return e;
    }

    @Test
    void ignora_offering_upserted() {
        consumer.consume(event("OfferingUpserted"));
        verify(repository, never()).save(any());
    }

    @Test
    void ignora_booking_state_snapshot() {
        consumer.consume(event("BookingStateSnapshot"));
        verify(repository, never()).save(any());
    }

    @Test
    void guarda_eventos_de_negocio() {
        BusinessEvent created = event("OfferingCreated");
        consumer.consume(created);
        verify(repository).save(created);
    }
}
