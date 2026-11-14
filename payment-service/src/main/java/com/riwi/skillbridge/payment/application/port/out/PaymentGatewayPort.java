package com.riwi.skillbridge.payment.application.port.out;

import com.riwi.skillbridge.payment.domain.model.PaymentCommand;
import com.riwi.skillbridge.payment.domain.model.PaymentStatus;

/** Pasarela de pagos (Stripe). Solo crea PaymentIntents; los datos de tarjeta nunca pasan por este servicio. */
public interface PaymentGatewayPort {

    /** {@code false} si falta la clave secreta: los endpoints de pago responden 503. */
    boolean isConfigured();

    /**
     * @throws com.riwi.skillbridge.payment.domain.exception.PaymentGatewayUnavailableException si la pasarela
     *         no está configurada o no responde (no se cachea: el cliente puede reintentar)
     */
    GatewayResult createPaymentIntent(PaymentCommand command);

    record GatewayResult(PaymentStatus status, String paymentIntentId, String clientSecret, String message) {}
}
