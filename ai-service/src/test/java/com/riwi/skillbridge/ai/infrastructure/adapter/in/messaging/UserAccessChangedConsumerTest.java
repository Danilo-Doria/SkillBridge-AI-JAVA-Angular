package com.riwi.skillbridge.ai.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.ai.application.port.out.UserAccessRepositoryPort;
import com.riwi.skillbridge.ai.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.ai.domain.model.AccessStatus;
import com.riwi.skillbridge.ai.domain.model.UserAccess;
import com.riwi.skillbridge.ai.support.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code UserAccessChanged} v1 por el listener real (Kafka/PostgreSQL de Testcontainers), serializado como el
 * {@code KafkaTemplate} del monolito. Misma partición por clave (userId): un marcador posterior para el MISMO usuario
 * garantiza que los mensajes previos ya se procesaron.
 */
@SpringBootTest
@AutoConfigureMockMvc // reutiliza el contexto cacheado
class UserAccessChangedConsumerTest extends AbstractIntegrationTest {

    record UserAccessChangedPayload(UUID userId, String status, String role) {}

    private static KafkaProducer<String, String> producer;
    private static final JsonSerializer<BusinessEvent<?>> MONOLITH_SERIALIZER = new JsonSerializer<>();

    @Autowired UserAccessRepositoryPort repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void producer() {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @AfterAll
    static void close() {
        producer.close();
    }

    @Test
    void upsertsAndIgnoresDuplicateAndOlderEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID suspendedEvent = UUID.randomUUID();
        BusinessEvent<UserAccessChangedPayload> suspended = event(suspendedEvent, "UserAccessChanged", t1,
                new UserAccessChangedPayload(userId, "SUSPENDED", "CUSTOMER"));

        send(suspended);
        await(userId, a -> a.status() == AccessStatus.SUSPENDED);

        send(suspended); // duplicado (mismo eventId)
        send(event(UUID.randomUUID(), "UserAccessChanged", t1.minusSeconds(60),
                new UserAccessChangedPayload(userId, "ACTIVE", "ADMIN"))); // más antiguo
        // Marcador posterior, mismo usuario/partición: cuando se aplica, los anteriores ya se procesaron.
        UUID markerEvent = UUID.randomUUID();
        send(event(markerEvent, "UserAccessChanged", t1.plusSeconds(1),
                new UserAccessChangedPayload(userId, "SUSPENDED", "PROVIDER")));
        UserAccess afterMarker = await(userId, a -> a.role().equals("PROVIDER"));
        assertThat(afterMarker.status()).isEqualTo(AccessStatus.SUSPENDED);
        assertThat(jdbc.queryForObject("SELECT last_event_id::text FROM ai.user_access WHERE user_id = ?",
                String.class, userId)).isEqualTo(markerEvent.toString());

        // Reactivación posterior: se aplica.
        send(event(UUID.randomUUID(), "UserAccessChanged", t1.plusSeconds(5),
                new UserAccessChangedPayload(userId, "ACTIVE", "PROVIDER")));
        await(userId, a -> a.status() == AccessStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.user_access WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    void unknownEventTypeIsIgnored() throws Exception {
        UUID userId = UUID.randomUUID();
        send(event(UUID.randomUUID(), "UserProfileChanged", Instant.now(),
                new UserAccessChangedPayload(userId, "SUSPENDED", "CUSTOMER")));
        send(event(UUID.randomUUID(), "UserAccessChanged", Instant.now().plusSeconds(1),
                new UserAccessChangedPayload(userId, "ACTIVE", "CUSTOMER")));
        UserAccess access = await(userId, a -> true);
        assertThat(access).isEqualTo(new UserAccess(userId, AccessStatus.ACTIVE, "CUSTOMER"));
    }

    private static BusinessEvent<UserAccessChangedPayload> event(UUID eventId, String type, Instant occurredAt,
                                                                 UserAccessChangedPayload payload) {
        return new BusinessEvent<>(eventId, type, payload.userId().toString(), "User", occurredAt, "corr-" + eventId, 1,
                payload, UUID.randomUUID().toString(), "admin@test.local", "ADMIN", "UPDATE", "USER",
                payload.userId().toString());
    }

    private static void send(BusinessEvent<?> event) throws Exception {
        String json = new String(MONOLITH_SERIALIZER.serialize("booking-events", event), StandardCharsets.UTF_8);
        producer.send(new ProducerRecord<>("booking-events", event.aggregateId(), json)).get();
    }

    private UserAccess await(UUID userId, Predicate<UserAccess> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<UserAccess> found = repository.findById(userId).filter(condition);
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Timed out waiting for user_access row of " + userId);
    }
}
