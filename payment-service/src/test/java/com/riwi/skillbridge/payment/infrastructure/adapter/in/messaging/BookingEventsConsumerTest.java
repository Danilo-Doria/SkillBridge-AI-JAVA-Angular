package com.riwi.skillbridge.payment.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.payment.application.port.out.BookingViewRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.OfferingViewRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.MentorshipMode;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import com.riwi.skillbridge.payment.support.TestInfrastructure;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Proyección de booking-events con mensajes serializados EXACTAMENTE como el monolito ({@code JsonSerializer} de
 * Spring Kafka sobre el envelope {@code BusinessEvent}, payload aplanado con los campos de actor).
 */
@SpringBootTest
class BookingEventsConsumerTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry, true);
    }

    @Autowired BookingEventsConsumer consumer;
    @Autowired BookingViewRepositoryPort bookings;
    @Autowired OfferingViewRepositoryPort offerings;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentGatewayPort gateway;

    /** Copias de los payloads del monolito (application/port/out/event). */
    record BookingCreatedPayload(UUID bookingId, UUID offeringId, UUID customerId, Instant scheduledAt, String status) {}
    record BookingCancelledPayload(UUID bookingId, UUID offeringId, UUID customerId, String status) {}
    record OfferingUpsertedPayload(UUID id, UUID providerId, String title, String description, String category,
                                   BigDecimal price, boolean active, String mode, int durationHours) {}

    private static String monolithJson(BusinessEvent<?> event) {
        try (JsonSerializer<BusinessEvent<?>> serializer = new JsonSerializer<>()) {
            serializer.setAddTypeInfo(false);
            return new String(serializer.serialize("booking-events", event), StandardCharsets.UTF_8);
        }
    }

    private static BusinessEvent<?> envelope(UUID eventId, String type, String aggregateId, Instant occurredAt,
                                             Object payload, UUID actorId, String actorEmail) {
        return new BusinessEvent<>(eventId, type, aggregateId, "Booking", occurredAt, "corr-1", 1, payload,
                actorId == null ? null : actorId.toString(), actorEmail, "CUSTOMER", "CREATE", "BOOKING", aggregateId);
    }

    private void deliver(String key, String json) throws Exception {
        consumer.onMessage(new ConsumerRecord<>("booking-events", 0, 0L, key, json));
    }

    private int bookingRows(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM booking_view WHERE booking_id = ?", Integer.class, id);
    }

    @Test
    void bookingCreatedIsIdempotentAndTakesEmailOnlyFromTheCustomerActor() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID offering = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-12-01T15:00:00Z");
        Instant occurredAt = Instant.now();
        String json = monolithJson(envelope(UUID.randomUUID(), "BookingCreated", bookingId.toString(), occurredAt,
                new BookingCreatedPayload(bookingId, offering, customer, scheduledAt, "CREATED"), customer, "c@test.local"));

        deliver(bookingId.toString(), json);
        deliver(bookingId.toString(), json); // reentrega at-least-once

        assertThat(bookingRows(bookingId)).isEqualTo(1);
        BookingView view = bookings.findById(bookingId).orElseThrow();
        assertThat(view).isEqualTo(new BookingView(bookingId, offering, customer, "c@test.local", scheduledAt, "CREATED"));

        // Un actor distinto del cliente (p. ej. un admin) no aporta customerEmail.
        UUID other = UUID.randomUUID();
        deliver(other.toString(), monolithJson(envelope(UUID.randomUUID(), "BookingCreated", other.toString(), occurredAt,
                new BookingCreatedPayload(other, offering, customer, scheduledAt, "CREATED"), UUID.randomUUID(), "admin@test.local")));
        assertThat(bookings.findById(other).orElseThrow().customerEmail()).isNull();
    }

    @Test
    void bookingCancelledAppliesOnceAndOlderEventsAreIgnored() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID offering = UUID.randomUUID();
        Instant created = Instant.now().minus(1, ChronoUnit.MINUTES);
        String createdJson = monolithJson(envelope(UUID.randomUUID(), "BookingCreated", bookingId.toString(), created,
                new BookingCreatedPayload(bookingId, offering, customer, Instant.now().plus(3, ChronoUnit.DAYS), "CREATED"),
                customer, "c@test.local"));
        String cancelledJson = monolithJson(envelope(UUID.randomUUID(), "BookingCancelled", bookingId.toString(),
                Instant.now(), new BookingCancelledPayload(bookingId, offering, customer, "CANCELLED"), customer, "c@test.local"));

        deliver(bookingId.toString(), createdJson);
        deliver(bookingId.toString(), cancelledJson);
        deliver(bookingId.toString(), cancelledJson);
        deliver(bookingId.toString(), createdJson); // reentrega vieja: no resucita la reserva

        assertThat(bookingRows(bookingId)).isEqualTo(1);
        assertThat(bookings.findById(bookingId).orElseThrow().status()).isEqualTo("CANCELLED");
        assertThat(bookings.findById(bookingId).orElseThrow().customerEmail()).isEqualTo("c@test.local");
    }

    /** Payload de BookingStatusChanged / BookingStateSnapshot v1 (customerEmail opcional). */
    record BookingStatePayload(UUID bookingId, UUID offeringId, UUID customerId, Instant scheduledAt, String status,
                               String customerEmail) {}

    @Test
    void bookingStatusChangedUpdatesCreatedToConfirmedIdempotently() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID offering = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-12-10T15:00:00Z");
        Instant t1 = Instant.now().minusSeconds(30);
        deliver(bookingId.toString(), monolithJson(envelope(UUID.randomUUID(), "BookingCreated", bookingId.toString(), t1,
                new BookingCreatedPayload(bookingId, offering, customer, scheduledAt, "CREATED"), customer, "c@test.local")));
        String confirmed = monolithJson(envelope(UUID.randomUUID(), "BookingStatusChanged", bookingId.toString(),
                Instant.now(), new BookingStatePayload(bookingId, offering, customer, scheduledAt, "CONFIRMED", null),
                UUID.randomUUID(), "system@test.local"));

        deliver(bookingId.toString(), confirmed);
        deliver(bookingId.toString(), confirmed); // duplicado

        assertThat(bookingRows(bookingId)).isEqualTo(1);
        assertThat(bookings.findById(bookingId).orElseThrow())
                .isEqualTo(new BookingView(bookingId, offering, customer, "c@test.local", scheduledAt, "CONFIRMED"));
    }

    @Test
    void bookingStateSnapshotCreatesMissingRowButOlderSnapshotDoesNotOverrideNewerState() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID offering = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-12-11T10:00:00Z");

        // Snapshot de una reserva que payment-service no conocía: crea la fila.
        Instant snapshotAt = Instant.now().minusSeconds(60);
        deliver(bookingId.toString(), monolithJson(envelope(UUID.randomUUID(), "BookingStateSnapshot",
                bookingId.toString(), snapshotAt,
                new BookingStatePayload(bookingId, offering, customer, scheduledAt, "CREATED", "snap@test.local"),
                null, null)));
        assertThat(bookings.findById(bookingId).orElseThrow())
                .isEqualTo(new BookingView(bookingId, offering, customer, "snap@test.local", scheduledAt, "CREATED"));

        // Transición más nueva y, después, un snapshot más viejo (reentrega/arranque): no pisa el estado nuevo.
        deliver(bookingId.toString(), monolithJson(envelope(UUID.randomUUID(), "BookingStatusChanged",
                bookingId.toString(), Instant.now(),
                new BookingStatePayload(bookingId, offering, customer, scheduledAt, "COMPLETED", null), null, null)));
        deliver(bookingId.toString(), monolithJson(envelope(UUID.randomUUID(), "BookingStateSnapshot",
                bookingId.toString(), snapshotAt.plusSeconds(1),
                new BookingStatePayload(bookingId, offering, customer, scheduledAt, "CREATED", null), null, null)));

        assertThat(bookingRows(bookingId)).isEqualTo(1);
        assertThat(bookings.findById(bookingId).orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(bookings.findById(bookingId).orElseThrow().isPaid()).isTrue();
    }

    @Test
    void bookingStatusChangedWithoutStatusIsRejected() {
        UUID bookingId = UUID.randomUUID();
        assertThatThrownBy(() -> deliver(bookingId.toString(), monolithJson(envelope(UUID.randomUUID(),
                "BookingStatusChanged", bookingId.toString(), Instant.now(),
                new BookingStatePayload(bookingId, null, UUID.randomUUID(), null, null, null), null, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bookingRows(bookingId)).isZero();
    }

    @Test
    void offeringUpsertedIsIdempotentAndIgnoresUnknownFields() throws Exception {
        UUID offering = UUID.randomUUID();
        UUID provider = UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(10);
        String v1 = monolithJson(envelope(UUID.randomUUID(), "OfferingUpserted", offering.toString(), t1,
                new OfferingUpsertedPayload(offering, provider, "Java", "desc", "dev", new BigDecimal("40.00"), true, "LIVE", 2),
                provider, "p@test.local"));
        String v2 = monolithJson(envelope(UUID.randomUUID(), "OfferingUpserted", offering.toString(), Instant.now(),
                new OfferingUpsertedPayload(offering, provider, "Java 21", "desc", "dev", new BigDecimal("45.50"), true,
                        "ASYNCHRONOUS", 4), provider, "p@test.local"));

        deliver(offering.toString(), v1);
        deliver(offering.toString(), v2);
        deliver(offering.toString(), v1); // vieja: ignorada
        deliver(offering.toString(), v2); // duplicada: ignorada

        assertThat(offerings.findById(offering).orElseThrow())
                .isEqualTo(new OfferingView(offering, provider, "Java 21", new BigDecimal("45.50"), true,
                        MentorshipMode.ASYNCHRONOUS, 4));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offering_view WHERE offering_id = ?", Integer.class, offering))
                .isEqualTo(1);
    }

    @Test
    void otherEventTypesAreIgnoredAndMalformedEventsAreRejected() throws Exception {
        UUID id = UUID.randomUUID();
        deliver(id.toString(), monolithJson(envelope(UUID.randomUUID(), "RecommendationGenerated", id.toString(),
                Instant.now(), Map.of("foo", "bar"), null, null)));
        assertThat(bookingRows(id)).isZero();

        assertThatThrownBy(() -> deliver(id.toString(), "{\"eventType\":\"BookingCreated\",\"eventId\":\"" + UUID.randomUUID()
                + "\",\"occurredAt\":1762351200.5,\"payload\":{\"bookingId\":\"" + id + "\"}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bookingRows(id)).isZero();
    }

    @Test
    void listenerConsumesBookingEventsTopicEndToEnd() {
        UUID bookingId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        String json = monolithJson(envelope(UUID.randomUUID(), "BookingCreated", bookingId.toString(), Instant.now(),
                new BookingCreatedPayload(bookingId, UUID.randomUUID(), customer, Instant.now().plus(2, ChronoUnit.DAYS), "CREATED"),
                customer, "e2e@test.local"));
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestInfrastructure.KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("booking-events", bookingId.toString(), json));
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(bookings.findById(bookingId)).hasValueSatisfying(v ->
                        assertThat(v.customerEmail()).isEqualTo("e2e@test.local")));
    }
}
