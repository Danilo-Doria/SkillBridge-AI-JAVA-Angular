package com.riwi.skillbridge.payment.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Solicitud de pago de una reserva. Nunca transporta datos de tarjeta: el cobro se completa en
 * Stripe Elements (navegador) con el {@code clientSecret} del PaymentIntent.
 *
 * @param idempotencyKey cabecera {@code Idempotency-Key} del cliente (evita cobros dobles)
 * @param customerId     actor autenticado (claim {@code uid} del JWT)
 */
public record PaymentCommand(
    String idempotencyKey,
    UUID bookingId,
    BigDecimal amount,
    UUID customerId
) {}
