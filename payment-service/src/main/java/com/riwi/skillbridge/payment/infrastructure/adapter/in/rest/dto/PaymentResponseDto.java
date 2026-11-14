package com.riwi.skillbridge.payment.infrastructure.adapter.in.rest.dto;

import java.util.UUID;

/** Misma respuesta que el monolito ({@code PaymentResponseDto}). */
public record PaymentResponseDto(
    UUID bookingId,
    String status,
    String message,
    String clientSecret
) {}
