package com.riwi.skillbridge.infrastructure.adapter.in.startup;

import com.riwi.skillbridge.application.port.in.PublishBookingSnapshotUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * HU-104 (E1): al arrancar publica un {@code BookingStateSnapshot} por reserva abierta (CREATED/CONFIRMED), para que
 * payment-service conozca reservas anteriores a su despliegue o a la retención de Kafka. Idempotente.
 */
@Component
@ConditionalOnProperty(name = "app.bookings.snapshot-on-startup", havingValue = "true")
public class BookingSnapshotOnStartup {

    private static final Logger log = LoggerFactory.getLogger(BookingSnapshotOnStartup.class);
    private final PublishBookingSnapshotUseCase snapshot;

    public BookingSnapshotOnStartup(PublishBookingSnapshotUseCase snapshot) {
        this.snapshot = snapshot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            log.info("Snapshot de reservas publicado: {} BookingStateSnapshot", snapshot.publishOpenBookingsSnapshot());
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el snapshot de reservas", e);
        }
    }
}
