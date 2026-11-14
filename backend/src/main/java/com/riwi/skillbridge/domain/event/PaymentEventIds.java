package com.riwi.skillbridge.domain.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Identificadores deterministas para eventos de pago (HU-104): el mismo hecho de origen (evento de Stripe,
 * pago, reserva vencida) produce siempre el mismo {@code eventId}, de modo que un reintento del productor
 * (webhook reentregado, publicación repetida tras un timeout) lo descarta la idempotencia del consumidor.
 */
public final class PaymentEventIds {
    private PaymentEventIds() { }

    public static UUID of(String eventType, String sourceKey) {
        return UUID.nameUUIDFromBytes((eventType + ":" + sourceKey).getBytes(StandardCharsets.UTF_8));
    }
}
