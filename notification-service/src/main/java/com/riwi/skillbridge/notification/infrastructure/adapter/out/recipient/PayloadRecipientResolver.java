package com.riwi.skillbridge.notification.infrastructure.adapter.out.recipient;

import com.riwi.skillbridge.notification.application.port.out.RecipientResolverPort;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.Recipient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Toma el destinatario del propio evento (ECST). No consulta la tabla {@code users} del monolito
 * (FR-010). Si el evento no lo trae, el caso de uso lo trata como error permanente (DLT).
 */
@Component
public class PayloadRecipientResolver implements RecipientResolverPort {

    @Override
    public Optional<Recipient> resolve(NotificationRequest request) {
        return request.embeddedRecipient();
    }
}
