package com.riwi.skillbridge.payment.application.port.in;

import com.riwi.skillbridge.payment.domain.model.PaymentCommand;
import com.riwi.skillbridge.payment.domain.model.PaymentResult;

public interface AuthorizePaymentUseCase {
    /** Crea (o recupera por Idempotency-Key) el PaymentIntent de una reserva del actor autenticado. */
    PaymentResult authorize(PaymentCommand command);
}
