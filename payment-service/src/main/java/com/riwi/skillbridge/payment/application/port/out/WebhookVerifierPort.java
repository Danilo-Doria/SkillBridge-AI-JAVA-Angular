package com.riwi.skillbridge.payment.application.port.out;

/** Verificación criptográfica del webhook de Stripe sobre el cuerpo crudo. */
public interface WebhookVerifierPort {

    /** {@code false} si falta el secreto de firma: el webhook responde 503 y Stripe reintenta más tarde. */
    boolean isConfigured();

    /**
     * @throws com.riwi.skillbridge.payment.domain.exception.InvalidWebhookException si la firma no es válida,
     *         ha caducado o el cuerpo firmado es ilegible
     */
    VerifiedWebhookEvent verify(String rawPayload, String signatureHeader);

    /**
     * Datos mínimos de un evento verificado. {@code bookingId} es el valor crudo de {@code metadata.bookingId}.
     */
    record VerifiedWebhookEvent(
        String eventId,
        String type,
        String objectType,
        String paymentIntentId,
        String bookingId,
        String failureMessage
    ) {}
}
