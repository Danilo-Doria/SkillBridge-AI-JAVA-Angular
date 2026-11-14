package com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import com.riwi.skillbridge.notification.domain.model.Recipient;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload JSON de {@code notification-requests}: mismos campos que {@code NotificationMessage} del monolito.
 * {@code recipientEmail}/{@code recipientName} son opcionales (ECST, "destinatario" en el contrato); hoy el
 * monolito no los envía.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationPayload(
    UUID eventId,
    UUID bookingId,
    UUID userId,
    NotificationType notificationType,
    Instant occurredAt,
    Instant paymentDeadline,
    String recipientEmail,
    String recipientName
) {

    /** @throws IllegalArgumentException si faltan campos obligatorios (error determinista: va a la DLT). */
    public NotificationRequest toDomain() {
        try {
            Recipient recipient = recipientEmail == null || recipientEmail.isBlank()
                ? null : new Recipient(recipientEmail, recipientName);
            return new NotificationRequest(eventId, bookingId, userId, notificationType, occurredAt,
                paymentDeadline, recipient);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Payload de notificación inválido: " + e.getMessage(), e);
        }
    }
}
