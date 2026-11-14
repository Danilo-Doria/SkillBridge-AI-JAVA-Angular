package com.riwi.skillbridge.infrastructure.adapter.in.startup;

import com.riwi.skillbridge.application.port.in.PublishUserAccessSnapshotUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * HU-104 (S1): al arrancar publica un {@code UserAccessChanged} por cada usuario suspendido, para que los servicios
 * extraídos conozcan las revocaciones anteriores a su despliegue o a la retención de Kafka. Idempotente.
 */
@Component
@ConditionalOnProperty(name = "app.users.access-snapshot-on-startup", havingValue = "true")
public class UserAccessSnapshotOnStartup {

    private static final Logger log = LoggerFactory.getLogger(UserAccessSnapshotOnStartup.class);
    private final PublishUserAccessSnapshotUseCase snapshot;

    public UserAccessSnapshotOnStartup(PublishUserAccessSnapshotUseCase snapshot) {
        this.snapshot = snapshot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            log.info("Snapshot de accesos publicado: {} UserAccessChanged", snapshot.publishRestrictedUsersSnapshot());
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el snapshot de accesos", e);
        }
    }
}
