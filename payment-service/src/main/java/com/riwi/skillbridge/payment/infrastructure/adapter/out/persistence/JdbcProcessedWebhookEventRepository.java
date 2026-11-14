package com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.payment.application.port.out.ProcessedWebhookEventPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@code payment.processed_stripe_event}. {@code ON CONFLICT DO NOTHING}: con dos entregas concurrentes del mismo
 * evento, la segunda espera al commit de la primera y obtiene 0 filas (duplicado); si la primera se revierte
 * (Kafka no confirmó), la segunda inserta y publica.
 */
@Repository
public class JdbcProcessedWebhookEventRepository implements ProcessedWebhookEventPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProcessedWebhookEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean registerIfNew(String stripeEventId, String eventType, UUID bookingId, String paymentIntentId) {
        return jdbc.update("""
                INSERT INTO processed_stripe_event (stripe_event_id, event_type, booking_id, payment_intent_id)
                VALUES (:id, :type, :bookingId, :intentId)
                ON CONFLICT (stripe_event_id) DO NOTHING
                """, new MapSqlParameterSource()
            .addValue("id", stripeEventId)
            .addValue("type", eventType)
            .addValue("bookingId", bookingId)
            .addValue("intentId", paymentIntentId)) > 0;
    }

    @Override
    public boolean hasSucceededPayment(UUID bookingId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM processed_stripe_event WHERE booking_id = :bookingId AND event_type = :type)
                """, new MapSqlParameterSource().addValue("bookingId", bookingId).addValue("type", PAYMENT_SUCCEEDED),
            Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
