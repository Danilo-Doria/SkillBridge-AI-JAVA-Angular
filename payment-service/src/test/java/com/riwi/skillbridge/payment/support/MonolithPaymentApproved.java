package com.riwi.skillbridge.payment.support;

import java.time.Instant;
import java.util.UUID;

/**
 * Fixture: copia campo a campo de {@code com.riwi.skillbridge.domain.event.PaymentApproved} del monolito
 * (feature/104), que es el tipo al que {@code KafkaPaymentResultConsumer} convierte el payload con
 * {@code objectMapper.convertValue(event.payload(), PaymentApproved.class)}.
 */
public record MonolithPaymentApproved(
    int version,
    UUID eventId,
    Instant occurredAt,
    String correlationId,
    UUID bookingId,
    UUID paymentId,
    UUID customerId,
    String customerEmail,
    UUID offeringId,
    String offeringTitle,
    UUID providerId,
    Instant scheduledAt,
    int durationHours,
    Mode mode
) {
    public enum Mode { ASYNCHRONOUS, LIVE }

    /** Copia de {@code com.riwi.skillbridge.domain.event.PaymentRejected}. */
    public record Rejected(UUID bookingId, String reason) {}
}
