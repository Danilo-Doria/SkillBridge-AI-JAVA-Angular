package com.riwi.skillbridge.payment.application.port.out;

import java.util.UUID;

public interface ProcessedWebhookEventPort {

    String PAYMENT_SUCCEEDED = "payment_intent.succeeded";
    String PAYMENT_FAILED = "payment_intent.payment_failed";

    /**
     * Registra el evento de Stripe dentro de la transacción en curso.
     *
     * @return {@code false} si ya estaba registrado (duplicado: no se repite ningún efecto)
     */
    boolean registerIfNew(String stripeEventId, String eventType, UUID bookingId, String paymentIntentId);

    boolean hasSucceededPayment(UUID bookingId);
}
