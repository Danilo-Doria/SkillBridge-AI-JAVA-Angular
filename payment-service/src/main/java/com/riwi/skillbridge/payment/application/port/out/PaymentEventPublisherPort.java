package com.riwi.skillbridge.payment.application.port.out;

import com.riwi.skillbridge.payment.domain.event.PaymentApproved;
import com.riwi.skillbridge.payment.domain.event.PaymentRejected;

import java.util.UUID;

/**
 * Publica en {@code payment-events} (clave = bookingId, envelope BusinessEvent). Síncrono: espera el ack del broker
 * y lanza {@link com.riwi.skillbridge.payment.domain.exception.MessagePublishException} si no llega.
 */
public interface PaymentEventPublisherPort {

    void publishApproved(PaymentApproved event);

    /** @param eventId id estable (p. ej. derivado del evento de Stripe) para que los reintentos sean deduplicables */
    void publishRejected(PaymentRejected event, UUID eventId);
}
