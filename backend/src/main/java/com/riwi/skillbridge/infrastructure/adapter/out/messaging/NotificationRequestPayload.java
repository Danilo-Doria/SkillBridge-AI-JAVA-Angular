package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload en el cable de {@code notification-requests} (HU-104, ECST): los campos de {@link NotificationMessage}
 * más el destinatario, para que {@code notification-service} envíe sin leer la tabla {@code users} (FR-010).
 * {@code recipientEmail}/{@code recipientName} son aditivos y opcionales: un consumidor antiguo los ignora.
 */
public record NotificationRequestPayload(
    UUID eventId,
    UUID bookingId,
    UUID userId,
    NotificationType notificationType,
    Instant occurredAt,
    Instant paymentDeadline,
    String recipientEmail,
    String recipientName
) {
    static NotificationRequestPayload of(NotificationMessage m, String recipientEmail, String recipientName) {
        return new NotificationRequestPayload(m.eventId(), m.bookingId(), m.userId(), m.notificationType(),
            m.occurredAt(), m.paymentDeadline(), recipientEmail, recipientName);
    }
}
