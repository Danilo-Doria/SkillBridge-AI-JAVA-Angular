package com.riwi.skillbridge.payment.domain.exception;

/** El broker no confirmó la publicación: el llamante no debe dar el pago por notificado (Stripe reintentará). */
public class MessagePublishException extends RuntimeException {
    public MessagePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
