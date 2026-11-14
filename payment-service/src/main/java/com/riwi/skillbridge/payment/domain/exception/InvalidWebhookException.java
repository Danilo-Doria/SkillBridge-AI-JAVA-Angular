package com.riwi.skillbridge.payment.domain.exception;

/** 400: webhook sin firma, con firma inválida/caducada o con cuerpo ilegible. Se lanza antes de cualquier efecto. */
public class InvalidWebhookException extends RuntimeException {
    public InvalidWebhookException(String message) {
        super(message);
    }
}
