package com.riwi.skillbridge.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.notification.domain.model.NotificationRequest;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.BusinessEventEnvelope;
import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.NotificationPayload;
import com.riwi.skillbridge.notification.infrastructure.config.KafkaConsumerConfiguration;
import com.riwi.skillbridge.notification.support.MonolithEnvelopes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato {@code notification-requests}: el deserializador del servicio (el mismo que usa el listener) lee
 * el envelope con la forma que publica {@code KafkaNotificationPublisher} del monolito.
 */
class EnvelopeContractTest {

    // Equivalente al ObjectMapper de Spring Boot (JavaTimeModule, sin fallar por campos desconocidos).
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ErrorHandlingDeserializer<BusinessEventEnvelope> deserializer =
        KafkaConsumerConfiguration.envelopeDeserializer(objectMapper);

    private byte[] fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/contracts/notification-request-booking-created.json")) {
            return in.readAllBytes();
        }
    }

    private NotificationRequest toDomain(BusinessEventEnvelope envelope) throws Exception {
        return objectMapper.treeToValue(envelope.payload(), NotificationPayload.class).toDomain();
    }

    @Test
    void fixtureDeserializesIgnoringMonolithTypeHeader() throws Exception {
        RecordHeaders headers = new RecordHeaders();
        headers.add("__TypeId__", "com.riwi.skillbridge.application.port.out.event.BusinessEvent"
            .getBytes(StandardCharsets.UTF_8));

        BusinessEventEnvelope envelope = deserializer.deserialize("notification-requests", headers, fixture());

        assertThat(envelope.eventId()).isEqualTo(UUID.fromString("6f1c2a52-6a1e-4b8e-9b9a-0c2d4e6f8a10"));
        assertThat(envelope.eventType()).isEqualTo("BookingCreatedNotification");
        assertThat(envelope.aggregateId()).isEqualTo("2b7e1516-28ae-4d2a-a6d2-abf7158809cf");
        assertThat(envelope.correlationId()).isEqualTo("corr-fixture-0001");
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-11-13T10:00:00.123456789Z"));

        NotificationRequest request = toDomain(envelope);
        assertThat(request.eventId()).isEqualTo(envelope.eventId());
        assertThat(request.bookingId()).isEqualTo(UUID.fromString("2b7e1516-28ae-4d2a-a6d2-abf7158809cf"));
        assertThat(request.userId()).isEqualTo(UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"));
        assertThat(request.notificationType()).isEqualTo(NotificationType.BOOKING_CREATED);
        // getPayloadForAudit usa un ObjectMapper con timestamps numéricos: segundos.nanos como número.
        assertThat(request.occurredAt().getEpochSecond()).isEqualTo(1794564000L);
        assertThat(request.paymentDeadline()).isNull();
        assertThat(request.recipient()).isNull();
    }

    @Test
    void fixtureHasSameShapeAsMonolithSerialization() throws Exception {
        UUID id = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-13T10:00:00.123456789Z");
        ProducerRecord<String, byte[]> produced = MonolithEnvelopes.record("notification-requests",
            MonolithEnvelopes.envelope(id, booking, NotificationType.BOOKING_CREATED, now, "c-1",
                new MonolithEnvelopes.CurrentNotificationMessage(id, booking, UUID.randomUUID(),
                    NotificationType.BOOKING_CREATED, now, null)));

        JsonNode actual = objectMapper.readTree(produced.value());
        JsonNode expected = objectMapper.readTree(fixture());
        assertThat(fieldNames(actual)).containsExactlyInAnyOrderElementsOf(fieldNames(expected));
        assertThat(fieldNames(actual.get("payload"))).containsExactlyInAnyOrderElementsOf(fieldNames(expected.get("payload")));
        assertThat(actual.get("payload").get("occurredAt").isNumber()).isTrue();
        assertThat(produced.key()).isEqualTo(booking.toString());
        assertThat(produced.headers().lastHeader("__TypeId__")).isNotNull();
    }

    @Test
    void paymentReminderWithDeadlineAndRecipientRoundTrips() throws Exception {
        UUID id = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        Instant deadline = Instant.parse("2026-11-20T15:30:00Z");
        ProducerRecord<String, byte[]> produced = MonolithEnvelopes.record("notification-requests",
            MonolithEnvelopes.envelope(id, booking, NotificationType.PAYMENT_REMINDER, Instant.now(), "c-2",
                new MonolithEnvelopes.EnrichedNotificationMessage(id, booking, UUID.randomUUID(),
                    NotificationType.PAYMENT_REMINDER, Instant.now(), deadline, "ana@example.com", "Ana")));

        BusinessEventEnvelope envelope =
            deserializer.deserialize("notification-requests", produced.headers(), produced.value());
        NotificationRequest request = toDomain(envelope);

        assertThat(envelope.eventType()).isEqualTo("PaymentReminderNotification");
        assertThat(request.paymentDeadline()).isEqualTo(deadline);
        assertThat(request.recipient().email()).isEqualTo("ana@example.com");
        assertThat(request.recipient().name()).isEqualTo("Ana");
    }

    @Test
    void brokenJsonDoesNotThrowFromDeserializer() {
        RecordHeaders headers = new RecordHeaders();
        BusinessEventEnvelope value = deserializer.deserialize("notification-requests", headers,
            "{not-json".getBytes(StandardCharsets.UTF_8));

        assertThat(value).isNull();
        assertThat(headers.lastHeader("springDeserializerExceptionValue")).isNotNull();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
