package com.riwi.skillbridge.payment.domain.exception;

/** 422. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
