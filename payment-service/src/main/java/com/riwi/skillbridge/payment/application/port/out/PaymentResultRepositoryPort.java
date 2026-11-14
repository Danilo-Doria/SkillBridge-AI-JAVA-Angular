package com.riwi.skillbridge.payment.application.port.out;

import com.riwi.skillbridge.payment.domain.model.PaymentResult;

import java.util.Optional;
import java.util.UUID;

public interface PaymentResultRepositoryPort {

    /** Idempotency-Key acotada al cliente: la clave de otro usuario nunca devuelve este resultado. */
    Optional<PaymentResult> findByIdempotencyKey(UUID customerId, String idempotencyKey);

    void save(UUID customerId, String idempotencyKey, PaymentResult result);

    Optional<UUID> findPaymentIdByIntent(String paymentIntentId);
}
