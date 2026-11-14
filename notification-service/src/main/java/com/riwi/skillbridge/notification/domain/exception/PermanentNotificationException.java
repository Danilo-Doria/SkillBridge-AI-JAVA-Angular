package com.riwi.skillbridge.notification.domain.exception;

/** Error determinista: reintentar no lo arregla, el mensaje va directo a la DLT. */
public class PermanentNotificationException extends RuntimeException {
    public PermanentNotificationException(String message) {
        super(message);
    }
}
