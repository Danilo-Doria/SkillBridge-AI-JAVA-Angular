package com.riwi.skillbridge.notification.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.idempotency.ProcessedEventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@code notification.processed_events} (Flyway V1). La PK {@code (consumer_group, event_id)} hace de
 * candado: un segundo insert concurrente espera al primero y, tras su commit, no inserta nada.
 */
@Repository
public class JdbcProcessedEventStore implements ProcessedEventStore {

    static final String INSERT = """
        INSERT INTO notification.processed_events (consumer_group, event_id, event_type)
        VALUES (?, ?, ?)
        ON CONFLICT (consumer_group, event_id) DO NOTHING
        """;

    private final JdbcTemplate jdbc;

    public JdbcProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(String consumerGroup, UUID eventId, String eventType) {
        return jdbc.update(INSERT, consumerGroup, eventId, eventType) == 1;
    }
}
