package com.riwi.skillbridge.payment.domain.event;

import java.util.UUID;

/** Misma forma que {@code PaymentRejected} del monolito; {@code occurredAt} viaja en el envelope. */
public record PaymentRejected(
    UUID bookingId,
    String reason
) {}
