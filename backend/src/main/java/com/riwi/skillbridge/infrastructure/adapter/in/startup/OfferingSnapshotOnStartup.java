package com.riwi.skillbridge.infrastructure.adapter.in.startup;

import com.riwi.skillbridge.application.port.in.PublishOfferingSnapshotUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Arranque en frío de read models externos (HU-104): al estar lista la aplicación publica un
 * {@code OfferingUpserted} por oferta (activas e inactivas). Desactivado por defecto ({@code app.catalog.snapshot-on-startup}).
 * Los consumidores son idempotentes (upsert por id), así que repetirlo es seguro.
 */
@Component
@ConditionalOnProperty(name = "app.catalog.snapshot-on-startup", havingValue = "true")
public class OfferingSnapshotOnStartup {

    private static final Logger log = LoggerFactory.getLogger(OfferingSnapshotOnStartup.class);
    private final PublishOfferingSnapshotUseCase snapshot;

    public OfferingSnapshotOnStartup(PublishOfferingSnapshotUseCase snapshot) {
        this.snapshot = snapshot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            int n = snapshot.publishActiveSnapshot();
            log.info("Snapshot de ofertas publicado: {} OfferingUpserted", n);
        } catch (RuntimeException e) {
            // No impide el arranque del monolito; el read model se completará con los eventos siguientes.
            log.error("No se pudo publicar el snapshot de ofertas", e);
        }
    }
}
