package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationSenderPort;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.service.PaymentApprovedAssembler;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaEventHeaders;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaNotificationPublisher;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaPaymentEventPublisher;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * HU-104 US4 contra Kafka y PostgreSQL reales, con ambos flags en {@code kafka} y SIN RabbitMQ:
 * reserva → notificación → pago aprobado → reserva CONFIRMED; duplicado = un solo efecto;
 * DLT tras 3 reintentos; mensaje ilegible a DLT sin bloquear la partición; correlación de extremo a extremo.
 */
@SpringBootTest(properties = {
    "app.jwt.generate-ephemeral-keys=true",
    "app.jwt.legacy-secret=integration-test-secret-with-at-least-32-characters",
    "app.cors.allowed-origins=http://localhost:4200",
    // Mismo backoff exponencial (3 reintentos, x2) pero más corto para que el test sea rápido.
    "app.messaging.kafka.retry.initial-interval=200",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration,"
        + "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration"
})
@Testcontainers
class KafkaMessagingFlowIT {

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("b7d9f3c1-a8e4-4c59-b1d6-8f2a3e9c4b7d");
    private static final String OWNER_EMAIL = "user@gmail.com";
    private static final String PAYMENTS = "payment-events";
    private static final String NOTIFICATIONS = "notification-requests";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
        .withDatabaseName("skillbridge_test").withUsername("test").withPassword("test");

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired ApplicationContext context;
    @Autowired CreateBookingUseCase createBooking;
    @Autowired PaymentEventPublisherPort paymentPublisher;
    @Autowired NotificationPublisherPort notificationPublisher;
    @Autowired PaymentApprovedAssembler approvedAssembler;
    @Autowired JpaBookingRepository bookingRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean NotificationSenderPort notificationSender;
    @MockitoBean BookingEventPublisherPort bookingEventPublisher;
    @MockitoBean AiRecommendationPort aiRecommendationPort;

    /** Lo que vio el "envío de email" en el hilo del consumidor: mensaje + correlación restaurada. */
    private final List<SentNotification> sent = new CopyOnWriteArrayList<>();
    private final Set<UUID> failingBookings = ConcurrentHashMap.newKeySet();

    record SentNotification(NotificationMessage message, String holderCorrelation, String mdcCorrelation) {
    }

    @BeforeEach
    void captureSends() {
        reset(notificationSender);
        doAnswer(inv -> {
            NotificationMessage message = inv.getArgument(0);
            sent.add(new SentNotification(message, CorrelationIdHolder.get(), MDC.get("correlationId")));
            if (failingBookings.contains(message.bookingId())) {
                throw new IllegalStateException("SMTP caído (simulado)");
            }
            return null;
        }).when(notificationSender).send(any());
    }

    @AfterEach
    void clearThread() {
        CorrelationIdHolder.clear();
    }

    @Test
    void rabbitBeansAreNotNeededAndKafkaAdaptersAreWired() {
        assertThat(paymentPublisher).isInstanceOf(KafkaPaymentEventPublisher.class);
        assertThat(notificationPublisher).isInstanceOf(KafkaNotificationPublisher.class);
        assertThat(context.getBeanDefinitionNames()).noneMatch(n -> n.toLowerCase().contains("rabbit"));
    }

    @Test
    void bookingThenApprovedPaymentConfirmsBookingWithCorrelationEndToEnd() throws Exception {
        String bookingCorrelation = "it-booking-" + UUID.randomUUID();
        CorrelationIdHolder.set(bookingCorrelation);
        UUID bookingId = createBooking.create(OFFERING_ID, Instant.now().plus(5, ChronoUnit.DAYS), OWNER_EMAIL).id();
        CorrelationIdHolder.clear();

        // notification-requests → consumidor → NotificationSenderPort, con la correlación de la petición en MDC.
        await().atMost(20, TimeUnit.SECONDS).until(() -> sentFor(bookingId).size() == 1);
        SentNotification notification = sentFor(bookingId).get(0);
        assertThat(notification.message().notificationType()).isEqualTo(NotificationType.BOOKING_CREATED);
        assertThat(notification.message().userId()).isEqualTo(OWNER_ID);
        assertThat(notification.holderCorrelation()).isEqualTo(bookingCorrelation);
        assertThat(notification.mdcCorrelation()).isEqualTo(bookingCorrelation);

        String paymentCorrelation = "it-payment-" + UUID.randomUUID();
        CorrelationIdHolder.set(paymentCorrelation);
        paymentPublisher.publishApproved(approvedAssembler.assemble(bookingId, UUID.randomUUID()));
        CorrelationIdHolder.clear();

        await().atMost(20, TimeUnit.SECONDS)
            .until(() -> bookingRepository.findById(bookingId).orElseThrow().getStatus() == BookingStatus.CONFIRMED);

        // En el cable: clave = bookingId, cabeceras del contrato y correlación también dentro del envelope.
        ConsumerRecord<String, byte[]> onWire = readFirst(PAYMENTS, r -> bookingId.toString().equals(r.key()));
        assertThat(header(onWire, KafkaEventHeaders.CORRELATION_ID)).isEqualTo(paymentCorrelation);
        assertThat(header(onWire, KafkaEventHeaders.EVENT_TYPE)).isEqualTo("PaymentApproved");
        JsonNode envelope = objectMapper.readTree(onWire.value());
        assertThat(envelope.get("correlationId").asText()).isEqualTo(paymentCorrelation);
        assertThat(envelope.get("payload").get("bookingId").asText()).isEqualTo(bookingId.toString());
        assertThat(processedCount(UUID.fromString(header(onWire, KafkaEventHeaders.EVENT_ID)))).isEqualTo(1);
    }

    @Test
    void duplicateEventHasSingleEffect() {
        UUID bookingId = persistCreatedBooking();
        PaymentApproved approved = new PaymentApproved(bookingId);
        BusinessEvent<PaymentApproved> event = new BusinessEvent<>(UUID.randomUUID(), "PaymentApproved",
            bookingId.toString(), "Payment", Instant.now(), "it-dup", 1, approved, null, null, null, null, null, null);

        kafkaTemplate.send(KafkaEventHeaders.record(PAYMENTS, event)).join();
        kafkaTemplate.send(KafkaEventHeaders.record(PAYMENTS, event)).join();
        // Centinela con la misma clave (misma partición, procesado después de los dos duplicados).
        BusinessEvent<PaymentRejected> sentinel = new BusinessEvent<>(UUID.randomUUID(), "PaymentRejected",
            bookingId.toString(), "Payment", Instant.now(), "it-dup", 1, new PaymentRejected(bookingId, "sentinel"),
            null, null, null, null, null, null);
        kafkaTemplate.send(KafkaEventHeaders.record(PAYMENTS, sentinel)).join();

        await().atMost(20, TimeUnit.SECONDS).until(() -> processedCount(sentinel.eventId()) == 1);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(processedCount(event.eventId())).isEqualTo(1);
        // Sin idempotencia la segunda entrega habría violado la regla CREATED→CONFIRMED y acabado en la DLT.
        assertThat(readAll(PAYMENTS + KafkaEventHeaders.DLT_SUFFIX, Duration.ofSeconds(4)))
            .noneMatch(r -> bookingId.toString().equals(r.key()));
    }

    @Test
    void failingNotificationGoesToDltAfterThreeRetriesWithErrorAndCorrelationHeaders() {
        UUID bookingId = UUID.randomUUID();
        failingBookings.add(bookingId);
        CorrelationIdHolder.set("it-dlt");
        NotificationMessage message = NotificationMessage.paymentReminder(bookingId, UUID.randomUUID());
        notificationPublisher.publish(message);
        CorrelationIdHolder.clear();

        ConsumerRecord<String, byte[]> dead = readFirst(NOTIFICATIONS + KafkaEventHeaders.DLT_SUFFIX,
            r -> bookingId.toString().equals(r.key()));

        assertThat(sentFor(bookingId)).hasSize(4); // 1 intento + 3 reintentos
        assertThat(header(dead, "kafka_dlt-exception-fqcn")).isNotBlank();
        assertThat(header(dead, "kafka_dlt-original-topic")).isEqualTo(NOTIFICATIONS);
        assertThat(header(dead, KafkaEventHeaders.CORRELATION_ID)).isEqualTo("it-dlt");
        assertThat(header(dead, KafkaEventHeaders.EVENT_ID)).isEqualTo(message.eventId().toString());
        assertThat(processedCount(message.eventId())).isZero(); // la marca se revirtió: se puede reinyectar
    }

    @Test
    void unreadableMessageGoesToDltWithoutBlockingPartition() {
        UUID bookingId = persistCreatedBooking();
        try (KafkaProducer<String, byte[]> raw = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            raw.send(new ProducerRecord<>(PAYMENTS, bookingId.toString(), "{not json".getBytes(StandardCharsets.UTF_8)));
            raw.flush();
        }
        paymentPublisher.publishApproved(new PaymentApproved(bookingId));

        ConsumerRecord<String, byte[]> dead = readFirst(PAYMENTS + KafkaEventHeaders.DLT_SUFFIX,
            r -> bookingId.toString().equals(r.key()));
        assertThat(new String(dead.value(), StandardCharsets.UTF_8)).isEqualTo("{not json");
        await().atMost(20, TimeUnit.SECONDS)
            .until(() -> bookingRepository.findById(bookingId).orElseThrow().getStatus() == BookingStatus.CONFIRMED);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UUID persistCreatedBooking() {
        UUID bookingId = UUID.randomUUID();
        bookingRepository.saveAndFlush(new BookingEntity(bookingId, OFFERING_ID, OWNER_ID,
            Instant.now().plus(7, ChronoUnit.DAYS), BookingStatus.CREATED, 0, Instant.now()));
        return bookingId;
    }

    private List<SentNotification> sentFor(UUID bookingId) {
        return sent.stream().filter(s -> s.message().bookingId().equals(bookingId)).toList();
    }

    private int processedCount(UUID eventId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private static String header(ConsumerRecord<String, byte[]> record, String name) {
        return KafkaEventHeaders.read(record.headers(), name);
    }

    private static ConsumerRecord<String, byte[]> readFirst(String topic, Predicate<ConsumerRecord<String, byte[]>> match) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        try (KafkaConsumer<String, byte[]> consumer = rawConsumer()) {
            assignFromBeginning(consumer, topic);
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) return record;
                }
            }
        }
        throw new AssertionError("No llegó el registro esperado a " + topic);
    }

    private static List<ConsumerRecord<String, byte[]>> readAll(String topic, Duration window) {
        List<ConsumerRecord<String, byte[]>> all = new ArrayList<>();
        long deadline = System.nanoTime() + window.toNanos();
        try (KafkaConsumer<String, byte[]> consumer = rawConsumer()) {
            assignFromBeginning(consumer, topic);
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(all::add);
            }
        }
        return all;
    }

    /** Sin grupo coordinado: asigna todas las particiones (espera a que exista el topic) y lee desde el principio. */
    private static void assignFromBeginning(KafkaConsumer<String, byte[]> consumer, String topic) {
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var partitions = consumer.partitionsFor(topic, Duration.ofSeconds(2));
            return partitions != null && !partitions.isEmpty();
        });
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
            .map(info -> new TopicPartition(topic, info.partition())).toList();
        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);
    }

    private static KafkaConsumer<String, byte[]> rawConsumer() {
        return new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "it-reader-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
    }
}
