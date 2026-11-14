package com.riwi.skillbridge.payment.infrastructure.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Mismo cuerpo que el monolito: solo reserva e importe; nunca datos de tarjeta. */
public record PaymentRequestDto(
    UUID bookingId,
    BigDecimal amount
) {}
