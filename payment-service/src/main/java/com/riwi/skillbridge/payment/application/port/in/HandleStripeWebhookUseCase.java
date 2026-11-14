package com.riwi.skillbridge.payment.application.port.in;

public interface HandleStripeWebhookUseCase {

    enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    /**
     * Verifica la firma sobre el cuerpo crudo ANTES de cualquier efecto y, si procede, publica el resultado del pago.
     * Idempotente por id de evento de Stripe.
     */
    Outcome handle(String rawPayload, String signatureHeader);
}
