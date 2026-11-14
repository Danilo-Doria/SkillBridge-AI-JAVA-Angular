package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.ProcessedEventStorePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tabla {@code processed_events} (V19). {@code ON CONFLICT DO NOTHING} hace la marca atómica incluso
 * con dos entregas concurrentes del mismo evento. Participa en la transacción JPA activa.
 */
@Component
public class ProcessedEventJdbcStore implements ProcessedEventStorePort {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventJdbcStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean markProcessed(String consumerGroup, UUID eventId, String eventType) {
        int inserted = jdbcTemplate.update(
            "INSERT INTO processed_events (consumer_group, event_id, event_type) VALUES (?, ?, ?) "
                + "ON CONFLICT (consumer_group, event_id) DO NOTHING",
            consumerGroup, eventId, eventType);
        return inserted == 1;
    }
}
