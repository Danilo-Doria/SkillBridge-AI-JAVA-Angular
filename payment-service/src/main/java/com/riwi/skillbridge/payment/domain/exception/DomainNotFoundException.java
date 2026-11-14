package com.riwi.skillbridge.payment.domain.exception;

/** 404. También se usa cuando la reserva existe pero no es del actor (mismo criterio que el monolito). */
public class DomainNotFoundException extends RuntimeException {
    public DomainNotFoundException(String message) {
        super(message);
    }
}
