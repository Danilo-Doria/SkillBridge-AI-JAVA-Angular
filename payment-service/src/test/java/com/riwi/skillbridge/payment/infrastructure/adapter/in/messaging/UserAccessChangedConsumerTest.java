package com.riwi.skillbridge.payment.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.application.port.out.UserAccessRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.payment.domain.model.AccessStatus;
import com.riwi.skillbridge.payment.domain.model.UserAccess;
import com.riwi.skillbridge.payment.support.TestInfrastructure;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Proyección de {@code UserAccessChanged} v1 sobre {@code payment.user_access}, serializado como el monolito. */
@SpringBootTest
@AutoConfigureMockMvc // mismo contexto cacheado que SecurityTest
class UserAccessChangedConsumerTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry, true);
    }

    @Autowired BookingEventsConsumer consumer;
    @Autowired UserAccessRepositoryPort userAccess;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentGatewayPort gateway;

    record UserAccessChangedPayload(UUID userId, String status, String role) {}

    static String userAccessJson(UUID eventId, Instant occurredAt, UUID userId, String status, String role) {
        BusinessEvent<?> event = new BusinessEvent<>(eventId, "UserAccessChanged", userId.toString(), "User", occurredAt,
                "corr-ua", 1, new UserAccessChangedPayload(userId, status, role), UUID.randomUUID().toString(),
                "admin@test.local", "ADMIN", "UPDATE", "USER", userId.toString());
        try (JsonSerializer<BusinessEvent<?>> serializer = new JsonSerializer<>()) {
            serializer.setAddTypeInfo(false);
            return new String(serializer.serialize("booking-events", event), StandardCharsets.UTF_8);
        }
    }

    private void deliver(String key, String json) throws Exception {
        consumer.onMessage(new ConsumerRecord<>("booking-events", 0, 0L, key, json));
    }

    private String lastEventId(UUID userId) {
        return jdbc.queryForObject("SELECT last_event_id::text FROM user_access WHERE user_id = ?", String.class, userId);
    }

    @Test
    void upsertsAndIgnoresDuplicateAndOlderEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(30);
        UUID suspendedEvent = UUID.randomUUID();
        String suspended = userAccessJson(suspendedEvent, t1, userId, "SUSPENDED", "CUSTOMER");

        deliver(userId.toString(), suspended);
        assertThat(userAccess.findById(userId)).contains(new UserAccess(userId, AccessStatus.SUSPENDED, "CUSTOMER"));

        deliver(userId.toString(), suspended); // duplicado
        deliver(userId.toString(), userAccessJson(UUID.randomUUID(), t1.minusSeconds(60), userId, "ACTIVE", "CUSTOMER"));
        assertThat(userAccess.findById(userId)).contains(new UserAccess(userId, AccessStatus.SUSPENDED, "CUSTOMER"));
        assertThat(lastEventId(userId)).isEqualTo(suspendedEvent.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_access WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);

        // Evento posterior (reactivación + cambio de rol): se aplica.
        deliver(userId.toString(), userAccessJson(UUID.randomUUID(), Instant.now(), userId, "ACTIVE", "PROVIDER"));
        assertThat(userAccess.findById(userId)).contains(new UserAccess(userId, AccessStatus.ACTIVE, "PROVIDER"));
    }

    @Test
    void unknownEventTypeIsIgnoredAndMalformedIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        deliver(userId.toString(), userAccessJson(UUID.randomUUID(), Instant.now(), userId, "SUSPENDED", "CUSTOMER")
                .replace("\"UserAccessChanged\"", "\"UserProfileChanged\""));
        assertThat(userAccess.findById(userId)).isEmpty();

        assertThatThrownBy(() -> deliver(userId.toString(),
                userAccessJson(UUID.randomUUID(), Instant.now(), userId, "BANNED", "CUSTOMER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(userAccess.findById(userId)).isEmpty();
    }

    @Test
    void listenerConsumesUserAccessChangedFromTopic() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestInfrastructure.KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("booking-events", userId.toString(),
                    userAccessJson(UUID.randomUUID(), Instant.now(), userId, "SUSPENDED", "PROVIDER")));
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(userAccess.findById(userId)).contains(new UserAccess(userId, AccessStatus.SUSPENDED, "PROVIDER")));
    }
}
