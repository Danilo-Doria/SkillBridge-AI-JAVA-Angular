package com.riwi.skillbridge.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationMessage(
    UUID eventId,
    UUID bookingId,
    UUID userId,
    NotificationType notificationType,
    Instant occurredAt,
    Instant paymentDeadline,
    // HU-104 (opcionales): destinatario ya conocido por el llamador; evita volver a leer users al publicar.
    String recipientEmail,
    String recipientName
) {
    public NotificationMessage {
        Objects.requireNonNull(eventId, "eventId es obligatorio");
        Objects.requireNonNull(bookingId, "bookingId es obligatorio");
        Objects.requireNonNull(userId, "userId es obligatorio");
        Objects.requireNonNull(notificationType, "notificationType es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }

    public NotificationMessage(UUID eventId, UUID bookingId, UUID userId, NotificationType notificationType, Instant occurredAt) {
        this(eventId, bookingId, userId, notificationType, occurredAt, null);
    }

    public NotificationMessage(UUID eventId, UUID bookingId, UUID userId, NotificationType notificationType,
                               Instant occurredAt, Instant paymentDeadline) {
        this(eventId, bookingId, userId, notificationType, occurredAt, paymentDeadline, null, null);
    }

    /** Copia con el destinatario ya resuelto por el llamador. */
    public NotificationMessage withRecipient(String email, String name) {
        return new NotificationMessage(eventId, bookingId, userId, notificationType, occurredAt, paymentDeadline, email, name);
    }

    public static NotificationMessage bookingCreated(UUID bookingId, UUID userId) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.BOOKING_CREATED, Instant.now(), null);
    }

    public static NotificationMessage bookingCancelled(UUID bookingId, UUID userId) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.BOOKING_CANCELLED, Instant.now(), null);
    }

    public static NotificationMessage paymentReminder(UUID bookingId, UUID userId) {
        return paymentReminder(bookingId, userId, null);
    }

    public static NotificationMessage paymentReminder(UUID bookingId, UUID userId, Instant paymentDeadline) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.PAYMENT_REMINDER, Instant.now(), paymentDeadline);
    }
}
