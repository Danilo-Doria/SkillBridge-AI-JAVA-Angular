package com.riwi.skillbridge.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Solicitud de notificación recibida por {@code notification-requests}. Mismos campos que
 * {@code NotificationMessage} del monolito más un destinatario opcional (ECST, ver contrato):
 * el servicio no puede leer la tabla {@code users} del monolito (FR-010).
 */
public record NotificationRequest(
    UUID eventId,
    UUID bookingId,
    UUID userId,
    NotificationType notificationType,
    Instant occurredAt,
    Instant paymentDeadline,
    Recipient recipient
) {
    public NotificationRequest {
        Objects.requireNonNull(eventId, "eventId es obligatorio");
        Objects.requireNonNull(bookingId, "bookingId es obligatorio");
        Objects.requireNonNull(userId, "userId es obligatorio");
        Objects.requireNonNull(notificationType, "notificationType es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }

    public Optional<Recipient> embeddedRecipient() {
        return Optional.ofNullable(recipient);
    }
}
