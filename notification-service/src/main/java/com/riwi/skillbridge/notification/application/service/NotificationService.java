package com.riwi.skillbridge.notification.application.service;

import com.riwi.skillbridge.notification.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.notification.application.port.out.EmailSenderPort;
import com.riwi.skillbridge.notification.application.port.out.RecipientResolverPort;
import com.riwi.skillbridge.notification.domain.exception.PermanentNotificationException;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.Recipient;
import com.riwi.skillbridge.notification.domain.service.NotificationTemplates;

/** Caso de uso: resolver destinatario, aplicar plantilla y enviar. Se cablea en la configuración. */
public class NotificationService implements ProcessNotificationUseCase {

    private final RecipientResolverPort recipients;
    private final EmailSenderPort emailSender;
    private final NotificationTemplates templates;

    public NotificationService(RecipientResolverPort recipients, EmailSenderPort emailSender,
                               NotificationTemplates templates) {
        this.recipients = recipients;
        this.emailSender = emailSender;
        this.templates = templates;
    }

    @Override
    public void process(NotificationRequest request) {
        Recipient recipient = recipients.resolve(request)
            .orElseThrow(() -> new PermanentNotificationException(
                "Sin destinatario para eventId=" + request.eventId() + " userId=" + request.userId()));
        emailSender.send(templates.render(request, recipient));
    }
}
