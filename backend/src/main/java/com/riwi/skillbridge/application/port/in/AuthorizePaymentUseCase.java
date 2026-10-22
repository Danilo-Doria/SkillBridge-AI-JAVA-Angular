package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;

public interface AuthorizePaymentUseCase {
    /**
     * Autoriza el pago de una reserva. Solo el cliente dueño de la reserva ({@code booking.customerId()})
     * puede pagarla; la verificación se hace antes de consultar la clave de idempotencia o la pasarela.
     *
     * @throws com.riwi.skillbridge.domain.exception.DomainNotFoundException si la reserva no existe
     * @throws com.riwi.skillbridge.domain.exception.ForbiddenOperationException si {@code actor} no es el dueño
     */
    PaymentResult authorize(PaymentCommand command, Actor actor);
}
