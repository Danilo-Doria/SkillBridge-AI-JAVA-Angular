package com.riwi.skillbridge.notification.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import com.riwi.skillbridge.notification.support.MonolithEnvelopes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Infraestructura real compartida (singleton por JVM): Kafka {@code cp-kafka:7.8.0}, Postgres
 * {@code pgvector:pg17} con el usuario/schema creados por {@code db/init-notification-schema.sql}
 * y Mailpit como SMTP. Cada clase usa su propio topic para no compartir grupo con otro contexto cacheado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractNotificationIntegrationTest {

    static final String DB_PASSWORD = "notification-test-pw";

    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
        .withCopyFileToContainer(MountableFile.forHostPath(Path.of("db/init-notification-schema.sql")),
            "/tmp/init-notification-schema.sql");
    @SuppressWarnings("resource")
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:latest")
        .withExposedPorts(1025, 8025)
        .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    static {
        KAFKA.start();
        POSTGRES.start();
        MAILPIT.start();
        runInitScript();
    }

    private static void runInitScript() {
        try {
            Container.ExecResult result = POSTGRES.execInContainer("psql", "-v", "ON_ERROR_STOP=1",
                "-v", "notification_password=" + DB_PASSWORD,
                "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
                "-f", "/tmp/init-notification-schema.sql");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("init-notification-schema.sql failed: " + result.getStderr());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void baseProperties(DynamicPropertyRegistry registry, String topic, int smtpPort) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "notification_svc");
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> smtpPort);
        registry.add("notification.kafka.topic", () -> topic);
    }

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    // ── Kafka helpers ───────────────────────────────────────────────────────

    void send(ProducerRecord<String, byte[]> record) throws Exception {
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            producer.send(record).get();
        }
    }

    static ProducerRecord<String, byte[]> enriched(String topic, UUID eventId, UUID bookingId,
                                                  NotificationType type, String correlationId, String email) {
        Instant now = Instant.now();
        return MonolithEnvelopes.record(topic, MonolithEnvelopes.envelope(eventId, bookingId, type, now,
            correlationId, new MonolithEnvelopes.EnrichedNotificationMessage(eventId, bookingId, UUID.randomUUID(),
                type, now, null, email, "Ana")));
    }

    /** Espera un registro en {@code topic} que cumpla {@code match} (consumidor nuevo desde el principio). */
    Optional<ConsumerRecord<String, byte[]>> awaitRecord(String topic, Predicate<ConsumerRecord<String, byte[]>> match,
                                                         Duration timeout) {
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) return Optional.of(record);
                }
            }
            return Optional.empty();
        }
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }

    // ── Mailpit helpers ─────────────────────────────────────────────────────

    /** Asuntos de los correos recibidos por {@code address}. */
    List<String> mailsTo(String address) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(
                "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/messages?limit=1000"))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        List<String> subjects = new ArrayList<>();
        for (JsonNode message : json.readTree(response.body()).path("messages")) {
            for (JsonNode to : message.path("To")) {
                if (address.equalsIgnoreCase(to.path("Address").asText())) {
                    subjects.add(message.path("Subject").asText());
                }
            }
        }
        return subjects;
    }

    int processedCount(UUID eventId) {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM notification.processed_events WHERE event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }
}
