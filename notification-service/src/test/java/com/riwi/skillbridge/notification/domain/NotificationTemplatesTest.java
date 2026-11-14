package com.riwi.skillbridge.notification.domain;

import com.riwi.skillbridge.notification.domain.model.EmailContent;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import com.riwi.skillbridge.notification.domain.model.Recipient;
import com.riwi.skillbridge.notification.domain.service.NotificationTemplates;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Los textos deben coincidir con {@code EmailNotificationSender} del monolito (copia literal). */
class NotificationTemplatesTest {

    private static final UUID BOOKING = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final NotificationTemplates templates = new NotificationTemplates();
    private final Recipient ana = new Recipient("ana@example.com", "Ana");

    private NotificationRequest request(NotificationType type, Instant deadline) {
        return new NotificationRequest(UUID.randomUUID(), BOOKING, UUID.randomUUID(), type, Instant.now(), deadline, ana);
    }

    @Test
    void bookingCreated() {
        EmailContent mail = templates.render(request(NotificationType.BOOKING_CREATED, null), ana);
        assertThat(mail.to()).isEqualTo("ana@example.com");
        assertThat(mail.subject()).isEqualTo("Reserva creada");
        assertThat(mail.body()).isEqualTo("Hola Ana, tu reserva " + BOOKING + " fue creada.");
    }

    @Test
    void bookingCancelled() {
        EmailContent mail = templates.render(request(NotificationType.BOOKING_CANCELLED, null), ana);
        assertThat(mail.subject()).isEqualTo("Reserva cancelada");
        assertThat(mail.body()).isEqualTo("Hola Ana, tu reserva " + BOOKING + " fue cancelada.");
    }

    @Test
    void paymentReminderWithDeadline() {
        EmailContent mail = templates.render(
            request(NotificationType.PAYMENT_REMINDER, Instant.parse("2026-11-20T15:30:00Z")), ana);
        assertThat(mail.subject()).isEqualTo("Recordatorio: Pago pendiente de tu mentoría");
        assertThat(mail.body()).isEqualTo("Hola Ana, tu reserva " + BOOKING + " está pendiente de pago."
            + " Recuerda que su límite de pago es hasta el 20 de noviembre a las 03:30 PM UTC.");
    }

    @Test
    void paymentReminderWithoutDeadline() {
        EmailContent mail = templates.render(request(NotificationType.PAYMENT_REMINDER, null), ana);
        assertThat(mail.body()).isEqualTo("Hola Ana, tu reserva " + BOOKING + " está pendiente de pago."
            + " Recuerda completar el pago para confirmar tu sesión antes del límite.");
    }

    @Test
    void fallsBackToEmailWhenNameMissing() {
        Recipient anon = new Recipient("x@example.com", null);
        EmailContent mail = templates.render(request(NotificationType.BOOKING_CREATED, null), anon);
        assertThat(mail.body()).startsWith("Hola x@example.com,");
    }
}
