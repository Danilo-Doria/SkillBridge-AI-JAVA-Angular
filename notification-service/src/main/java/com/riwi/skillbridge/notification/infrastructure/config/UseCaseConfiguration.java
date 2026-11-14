package com.riwi.skillbridge.notification.infrastructure.config;

import com.riwi.skillbridge.notification.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.notification.application.port.out.EmailSenderPort;
import com.riwi.skillbridge.notification.application.port.out.RecipientResolverPort;
import com.riwi.skillbridge.notification.application.service.NotificationService;
import com.riwi.skillbridge.notification.domain.service.NotificationTemplates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cableado del caso de uso: dominio y aplicación quedan sin anotaciones de Spring. */
@Configuration
public class UseCaseConfiguration {

    @Bean
    NotificationTemplates notificationTemplates() {
        return new NotificationTemplates();
    }

    @Bean
    ProcessNotificationUseCase processNotificationUseCase(RecipientResolverPort recipients,
                                                          EmailSenderPort emailSender,
                                                          NotificationTemplates templates) {
        return new NotificationService(recipients, emailSender, templates);
    }
}
