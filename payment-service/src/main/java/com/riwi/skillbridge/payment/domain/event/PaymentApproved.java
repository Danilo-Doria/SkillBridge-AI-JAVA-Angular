package com.riwi.skillbridge.payment.domain.event;

import com.riwi.skillbridge.payment.domain.model.MentorshipMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Pago aprobado. Misma forma que {@code com.riwi.skillbridge.domain.event.PaymentApproved} del monolito y que
 * {@code docs/contracts/mentorships/events.md} §2 (v2: 14 campos, ni uno más).
 *
 * <p>{@code paymentId} puede ser nulo (aprobación por webhook sin autorización previa conocida). Si el read model no
 * tiene todo el estado, se emite la forma mínima v1 (solo {@code bookingId} relevante), como hace el monolito.
 */
public record PaymentApproved(
    int version,
    UUID eventId,
    Instant occurredAt,
    String correlationId,
    UUID bookingId,
    UUID paymentId,
    UUID customerId,
    String customerEmail,
    UUID offeringId,
    String offeringTitle,
    UUID providerId,
    Instant scheduledAt,
    int durationHours,
    MentorshipMode mode
) {
    public static final int CURRENT_VERSION = 2;

    /** Forma mínima (v1). Conserva eventId/occurredAt para que los reintentos sean deduplicables. */
    public static PaymentApproved minimal(UUID eventId, Instant occurredAt, String correlationId, UUID bookingId) {
        return new PaymentApproved(1, eventId, occurredAt, correlationId, bookingId,
            null, null, null, null, null, null, null, 0, null);
    }

    /** Sin prefijo is/get a propósito: no debe serializarse como propiedad. */
    public boolean hasFullMentorshipState() {
        return version >= CURRENT_VERSION && customerId != null && customerEmail != null
            && offeringId != null && offeringTitle != null && providerId != null
            && scheduledAt != null && durationHours > 0 && mode != null;
    }
}
