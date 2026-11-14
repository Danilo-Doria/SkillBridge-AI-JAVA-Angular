package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-104: el payload de notification-requests lleva el destinatario (ECST) para notification-service. */
class KafkaNotificationRecipientTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, BusinessEvent<?>> template = mock(KafkaTemplate.class);
    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final KafkaNotificationPublisher publisher =
        new KafkaNotificationPublisher(template, new SimpleMeterRegistry(), "notification-requests", users);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> publishedPayload(NotificationMessage message) {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        publisher.publish(message);
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        BusinessEvent<?> event = (BusinessEvent<?>) captor.getValue().value();
        // Lo que viaja por el cable es el mapa serializado
        Map<String, Object> wire = event.getPayloadForAudit();
        // Y el consumidor del monolito sigue pudiendo reconstruir el NotificationMessage
        NotificationMessage back = KafkaEnvelopeWire.BOOT_LIKE_MAPPER.convertValue(wire, NotificationMessage.class);
        assertThat(back.bookingId()).isEqualTo(message.bookingId());
        assertThat(back.userId()).isEqualTo(message.userId());
        return wire;
    }

    @Test
    void incluye_email_y_nombre_del_destinatario() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(
            new UserAccount(userId, "Ana", "ana@example.test", "hash", Role.CUSTOMER)));

        Map<String, Object> wire = publishedPayload(NotificationMessage.bookingCreated(UUID.randomUUID(), userId));

        assertThat(wire).containsEntry("recipientEmail", "ana@example.test").containsEntry("recipientName", "Ana");
    }

    @Test
    void sin_usuario_publica_igual_sin_destinatario() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.empty());

        Map<String, Object> wire = publishedPayload(NotificationMessage.bookingCancelled(UUID.randomUUID(), userId));

        assertThat(wire.get("recipientEmail")).isNull();
    }

    @Test
    void fallo_al_resolver_no_rompe_la_publicacion() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenThrow(new IllegalStateException("db down"));

        Map<String, Object> wire = publishedPayload(NotificationMessage.paymentReminder(UUID.randomUUID(), userId));

        assertThat(wire.get("recipientEmail")).isNull();
    }
}
