package com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.payment.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.payment.domain.model.PaymentResult;
import com.riwi.skillbridge.payment.domain.model.PaymentStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.uuid;

/** {@code payment.payment_result}: sustituye al caché en memoria del monolito (sobrevive a reinicios). */
@Repository
public class JdbcPaymentResultRepository implements PaymentResultRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPaymentResultRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PaymentResult> findByIdempotencyKey(UUID customerId, String idempotencyKey) {
        return jdbc.query("""
                SELECT payment_id, booking_id, status, message, client_secret, payment_intent_id
                FROM payment_result WHERE customer_id = :customerId AND idempotency_key = :key
                """, new MapSqlParameterSource().addValue("customerId", customerId).addValue("key", idempotencyKey),
            (rs, n) -> new PaymentResult(uuid(rs, "payment_id"), uuid(rs, "booking_id"),
                PaymentStatus.valueOf(rs.getString("status")), rs.getString("message"),
                rs.getString("client_secret"), rs.getString("payment_intent_id")))
            .stream().findFirst();
    }

    @Override
    public void save(UUID customerId, String idempotencyKey, PaymentResult result) {
        jdbc.update("""
                INSERT INTO payment_result (payment_id, customer_id, idempotency_key, booking_id, status, message,
                                            client_secret, payment_intent_id)
                VALUES (:paymentId, :customerId, :key, :bookingId, :status, :message, :clientSecret, :intentId)
                ON CONFLICT ON CONSTRAINT uq_payment_result_idempotency DO NOTHING
                """, new MapSqlParameterSource()
            .addValue("paymentId", result.paymentId())
            .addValue("customerId", customerId)
            .addValue("key", idempotencyKey)
            .addValue("bookingId", result.bookingId())
            .addValue("status", result.status().name())
            .addValue("message", truncate(result.message()))
            .addValue("clientSecret", result.clientSecret())
            .addValue("intentId", result.paymentIntentId()));
    }

    @Override
    public Optional<UUID> findPaymentIdByIntent(String paymentIntentId) {
        return jdbc.query("""
                SELECT payment_id FROM payment_result WHERE payment_intent_id = :intentId
                ORDER BY created_at LIMIT 1
                """, new MapSqlParameterSource("intentId", paymentIntentId), (rs, n) -> uuid(rs, "payment_id"))
            .stream().findFirst();
    }

    private static String truncate(String message) {
        return message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
