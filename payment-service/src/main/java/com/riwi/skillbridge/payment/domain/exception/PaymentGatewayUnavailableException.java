package com.riwi.skillbridge.payment.domain.exception;

/** 503: Stripe no está configurado o no responde. Nunca se sustituye por un éxito simulado. */
public class PaymentGatewayUnavailableException extends RuntimeException {
    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
