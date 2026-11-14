package com.riwi.skillbridge.payment.domain.exception;

/** 409: la misma Idempotency-Key ya se usó para otra reserva. */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
