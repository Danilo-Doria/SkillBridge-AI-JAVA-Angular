package com.riwi.skillbridge.notification.domain.service;

import com.riwi.skillbridge.notification.domain.model.EmailContent;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import com.riwi.skillbridge.notification.domain.model.Recipient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Plantillas de correo. Copia literal de {@code EmailNotificationSender#subject/body} del monolito
 * (HU-104 WP-D): el original no se toca. Mientras el consumidor del monolito siga activo, un cambio
 * de texto debe hacerse en ambos sitios.
 */
public final class NotificationTemplates {

    private static final DateTimeFormatter DEADLINE_DATE_FORMATTER = DateTimeFormatter
        .ofPattern("dd 'de' MMMM", Locale.forLanguageTag("es"))
        .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter DEADLINE_TIME_FORMATTER = DateTimeFormatter
        .ofPattern("hh:mm a", Locale.US)
        .withZone(ZoneId.of("UTC"));

    public EmailContent render(NotificationRequest request, Recipient recipient) {
        return new EmailContent(recipient.email(), subject(request), body(recipient, request));
    }

    public String subject(NotificationRequest request) {
        return switch (request.notificationType()) {
            case BOOKING_CREATED -> "Reserva creada";
            case BOOKING_CANCELLED -> "Reserva cancelada";
            case PAYMENT_REMINDER -> "Recordatorio: Pago pendiente de tu mentoría";
        };
    }

    public String body(Recipient recipient, NotificationRequest request) {
        String name = recipient.displayName();
        if (request.notificationType() == NotificationType.PAYMENT_REMINDER) {
            String deadlinePart = request.paymentDeadline() != null
                ? " Recuerda que su límite de pago es hasta el "
                    + DEADLINE_DATE_FORMATTER.format(request.paymentDeadline())
                    + " a las " + DEADLINE_TIME_FORMATTER.format(request.paymentDeadline())
                    + " UTC."
                : " Recuerda completar el pago para confirmar tu sesión antes del límite.";
            return "Hola " + name + ", tu reserva " + request.bookingId() + " está pendiente de pago." + deadlinePart;
        }

        String action = switch (request.notificationType()) {
            case BOOKING_CREATED -> "fue creada";
            case BOOKING_CANCELLED -> "fue cancelada";
            case PAYMENT_REMINDER -> "";
        };
        return "Hola " + name + ", tu reserva " + request.bookingId() + " " + action + ".";
    }
}
