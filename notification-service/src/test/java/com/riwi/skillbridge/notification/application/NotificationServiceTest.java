package com.riwi.skillbridge.notification.application;

import com.riwi.skillbridge.notification.application.service.NotificationService;
import com.riwi.skillbridge.notification.domain.exception.PermanentNotificationException;
import com.riwi.skillbridge.notification.domain.model.EmailContent;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import com.riwi.skillbridge.notification.domain.model.Recipient;
import com.riwi.skillbridge.notification.domain.service.NotificationTemplates;
import com.riwi.skillbridge.notification.infrastructure.adapter.out.recipient.PayloadRecipientResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {

    private final List<EmailContent> sent = new ArrayList<>();
    private final NotificationService service =
        new NotificationService(new PayloadRecipientResolver(), sent::add, new NotificationTemplates());

    @Test
    void sendsRenderedEmailToEmbeddedRecipient() {
        service.process(new NotificationRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NotificationType.BOOKING_CREATED, Instant.now(), null, new Recipient("ana@example.com", "Ana")));

        assertThat(sent).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo("ana@example.com");
            assertThat(mail.subject()).isEqualTo("Reserva creada");
        });
    }

    @Test
    void missingRecipientIsPermanent() {
        NotificationRequest request = new NotificationRequest(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), NotificationType.BOOKING_CREATED, Instant.now(), null, null);

        assertThatThrownBy(() -> service.process(request)).isInstanceOf(PermanentNotificationException.class);
        assertThat(sent).isEmpty();
    }
}
