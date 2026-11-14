package com.riwi.skillbridge.notification.application.port.out;

import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.Recipient;

import java.util.Optional;

/** Obtiene el destinatario de una solicitud sin leer tablas de otro servicio (FR-010). */
public interface RecipientResolverPort {
    Optional<Recipient> resolve(NotificationRequest request);
}
