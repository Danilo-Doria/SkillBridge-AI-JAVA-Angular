package com.riwi.skillbridge.ai.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.ai.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.persistence.OfferingViewEntity;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.persistence.OfferingViewJpaRepository;
import com.riwi.skillbridge.ai.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumidor real contra Kafka/PostgreSQL de Testcontainers. Los mensajes se serializan con un {@link JsonSerializer}
 * por defecto, igual que el {@code KafkaTemplate} del monolito, usando el mismo record {@code BusinessEvent}.
 */
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc // reutiliza el contexto cacheado
class OfferingUpsertedConsumerTest extends AbstractIntegrationTest {

    /** Payload v1 acordado por el orquestador (T005). */
    record OfferingUpsertedPayload(UUID id, UUID providerId, String title, String description, String category,
                                   BigDecimal price, boolean active, String mode, int durationHours) {
    }

    private static KafkaProducer<String, String> producer;
    private static final JsonSerializer<BusinessEvent<?>> MONOLITH_SERIALIZER = new JsonSerializer<>();

    @Autowired OfferingViewJpaRepository repository;

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
    void upsertsNewOfferingAndIgnoresDuplicateAndOlderEvents() throws Exception {
        UUID offeringId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Instant t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID firstEventId = UUID.randomUUID();

        send(offeringEvent(firstEventId, t1, new OfferingUpsertedPayload(offeringId, providerId, "Spring Boot",
                "APIs REST", "Backend", new BigDecimal("40.00"), true, "LIVE", 2)));
        OfferingViewEntity created = await(() -> repository.findById(offeringId));
        assertThat(created.getTitle()).isEqualTo("Spring Boot");
        assertThat(created.getMode()).isEqualTo("LIVE");
        assertThat(created.getDurationHours()).isEqualTo(2);
        assertThat(created.getPrice()).isEqualByComparingTo("40.00");
        Instant firstUpdate = created.getUpdatedAt();

        // Duplicado (mismo eventId, entrega at-least-once) y evento más antiguo: no cambian la fila.
        send(offeringEvent(firstEventId, t1, new OfferingUpsertedPayload(offeringId, providerId, "DUPLICADO",
                "x", "x", new BigDecimal("1.00"), false, "LIVE", 2)));
        send(offeringEvent(UUID.randomUUID(), t1.minusSeconds(60), new OfferingUpsertedPayload(offeringId, providerId,
                "ANTIGUO", "x", "x", new BigDecimal("1.00"), false, "LIVE", 2)));
        // Marcador posterior en otra oferta: cuando se aplica, los anteriores de la partición ya se procesaron.
        UUID marker = UUID.randomUUID();
        send(offeringEvent(UUID.randomUUID(), t1, new OfferingUpsertedPayload(marker, providerId, "Marcador",
                null, null, BigDecimal.ZERO, true, null, 0)));
        OfferingViewEntity markerRow = await(() -> repository.findById(marker));
        assertThat(markerRow.getMode()).isEqualTo("ASYNCHRONOUS"); // normalización del dominio
        assertThat(markerRow.getDurationHours()).isEqualTo(1);

        OfferingViewEntity unchanged = repository.findById(offeringId).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Spring Boot");
        assertThat(unchanged.isActive()).isTrue();
        assertThat(unchanged.getUpdatedAt()).isEqualTo(firstUpdate);
        assertThat(repository.findAll().stream().filter(o -> o.getId().equals(offeringId))).hasSize(1);

        // Evento más reciente: actualiza (desactivación).
        send(offeringEvent(UUID.randomUUID(), t1.plusSeconds(5), new OfferingUpsertedPayload(offeringId, providerId,
                "Spring Boot avanzado", "APIs REST", "Backend", new BigDecimal("45.00"), false, "ASYNCHRONOUS", 3)));
        OfferingViewEntity updated = await(() -> repository.findById(offeringId)
                .filter(o -> o.getTitle().equals("Spring Boot avanzado")));
        assertThat(updated.isActive()).isFalse();
        assertThat(repository.findByActiveTrueOrderByTitleAsc()).noneMatch(o -> o.getId().equals(offeringId));
    }

    @Test
    void ignoresOtherEventTypes() throws Exception {
        UUID id = UUID.randomUUID();
        BusinessEvent<Map<String, Object>> other = new BusinessEvent<>(UUID.randomUUID(), "BookingCreated",
                id.toString(), "Booking", Instant.now(), "corr", 1, Map.of("id", id.toString(), "providerId",
                UUID.randomUUID().toString(), "title", "No soy oferta", "price", 1), null, null, null, null, null, null);
        send(other);
        UUID marker = UUID.randomUUID();
        send(offeringEvent(UUID.randomUUID(), Instant.now(), new OfferingUpsertedPayload(marker, UUID.randomUUID(),
                "Marcador 2", null, null, BigDecimal.ONE, true, "LIVE", 1)));
        await(() -> repository.findById(marker));
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void malformedOfferingGoesToServiceDeadLetterTopic() throws Exception {
        UUID badId = UUID.randomUUID();
        // price ausente: el dominio lo rechaza (IllegalArgumentException, no reintentable)
        String raw = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"OfferingUpserted\",\"aggregateId\":\""
                + badId + "\",\"aggregateType\":\"Offering\",\"occurredAt\":\"" + Instant.now() + "\",\"version\":1,"
                + "\"payload\":{\"id\":\"" + badId + "\",\"providerId\":\"" + UUID.randomUUID() + "\",\"title\":\"Sin precio\"}}";
        producer.send(new ProducerRecord<>("booking-events", badId.toString(), raw)).get();

        try (KafkaConsumer<String, String> dlt = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            dlt.subscribe(List.of("ai-service.booking-events.DLT"));
            boolean found = false;
            long deadline = System.currentTimeMillis() + 60_000;
            while (!found && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = dlt.poll(Duration.ofSeconds(1));
                for (var r : records) {
                    if (badId.toString().equals(r.key())) {
                        found = true;
                    }
                }
            }
            assertThat(found).as("mensaje mal formado en ai-service.booking-events.DLT").isTrue();
        }
        assertThat(repository.findById(badId)).isEmpty();
    }

    private static BusinessEvent<OfferingUpsertedPayload> offeringEvent(UUID eventId, Instant occurredAt,
                                                                        OfferingUpsertedPayload payload) {
        return new BusinessEvent<>(eventId, "OfferingUpserted", payload.id().toString(), "Offering", occurredAt,
                "corr-" + eventId, 1, payload, UUID.randomUUID().toString(), "provider@test.local", "PROVIDER",
                "UPSERT", "OFFERING", payload.id().toString());
    }

    private static void send(BusinessEvent<?> event) throws Exception {
        String json = new String(MONOLITH_SERIALIZER.serialize("booking-events", event), StandardCharsets.UTF_8);
        producer.send(new ProducerRecord<>("booking-events", event.aggregateId(), json)).get();
    }

    private static OfferingViewEntity await(Supplier<Optional<OfferingViewEntity>> query) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<OfferingViewEntity> found = query.get();
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Timed out waiting for offering_view row");
    }
}
