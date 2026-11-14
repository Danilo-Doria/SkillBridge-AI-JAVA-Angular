package com.riwi.skillbridge.payment.domain.model;

import java.util.UUID;

/**
 * Resultado de una autorización. {@code clientSecret} es el token de cliente que Stripe entrega para montar
 * Elements; {@code paymentIntentId} es el id {@code pi_...} de Stripe (uso interno, no se expone por REST).
 */
public record PaymentResult(
    UUID paymentId,
    UUID bookingId,
    PaymentStatus status,
    String message,
    String clientSecret,
    String paymentIntentId
) {
    public static PaymentResult declined(UUID bookingId, String message) {
        return new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.DECLINED, message, null, null);
    }
}
