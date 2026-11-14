package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaPaymentEventPublisherTest {

    private static final String TOPIC = "payment-events";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, BusinessEvent<?>> template = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final KafkaPaymentEventPublisher publisher =
        new KafkaPaymentEventPublisher(template, meters, TOPIC, Duration.ofSeconds(1));

    @AfterEach
    void clear() {
        CorrelationIdHolder.clear();
    }

    @Test
    void publishesApprovedV2InEnvelopeKeyedByBookingWithContractHeaders() throws Exception {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        PaymentApproved event = approvedV2("corr-approved");

        publisher.publishApproved(event);

        ProducerRecord<String, BusinessEvent<?>> record = captured();
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo(event.bookingId().toString());
        BusinessEvent<?> envelope = record.value();
        assertThat(envelope.eventId()).isEqualTo(event.eventId());
        assertThat(envelope.eventType()).isEqualTo("PaymentApproved");
        assertThat(envelope.correlationId()).isEqualTo("corr-approved");
        assertThat(envelope.version()).isEqualTo(2);
        assertThat(KafkaEventHeaders.read(record.headers(), KafkaEventHeaders.CORRELATION_ID)).isEqualTo("corr-approved");
        assertThat(KafkaEventHeaders.read(record.headers(), KafkaEventHeaders.EVENT_ID)).isEqualTo(event.eventId().toString());
        assertThat(KafkaEventHeaders.read(record.headers(), KafkaEventHeaders.EVENT_TYPE)).isEqualTo("PaymentApproved");
        assertThat(KafkaEventHeaders.read(record.headers(), KafkaEventHeaders.SCHEMA_VERSION)).isEqualTo("2");

        // Contrato en el cable: payload ECST completo (incluye mode y durationHours) y correlación en el envelope.
        JsonNode json = KafkaEnvelopeWire.BOOT_LIKE_MAPPER.readTree(KafkaEnvelopeWire.serialize(TOPIC, envelope));
        assertThat(json.get("correlationId").asText()).isEqualTo("corr-approved");
        assertThat(json.get("payload").get("mode").asText()).isEqualTo("LIVE");
        assertThat(json.get("payload").get("durationHours").asInt()).isEqualTo(2);
        assertThat(json.get("payload").get("customerEmail").asText()).isEqualTo("c@x.test");
    }

    @Test
    void rejectedUsesCorrelationFromCurrentRequest() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        CorrelationIdHolder.set("corr-http");
        UUID bookingId = UUID.randomUUID();

        publisher.publishRejected(new PaymentRejected(bookingId, "card declined"));

        ProducerRecord<String, BusinessEvent<?>> record = captured();
        assertThat(record.key()).isEqualTo(bookingId.toString());
        assertThat(record.value().eventType()).isEqualTo("PaymentRejected");
        assertThat(record.value().correlationId()).isEqualTo("corr-http");
        assertThat(KafkaEventHeaders.read(record.headers(), KafkaEventHeaders.CORRELATION_ID)).isEqualTo("corr-http");
    }

    @Test
    void minimalV1EventGetsEnvelopeEventId() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishApproved(new PaymentApproved(UUID.randomUUID()));

        assertThat(captured().value().eventId()).isNotNull();
    }

    @Test
    void brokerFailureIsNotSilentForPayments() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> publisher.publishApproved(approvedV2("c")))
            .isInstanceOf(MessagePublishException.class);
        assertThat(meters.counter("skillbridge.messaging.publish.failures",
            "topic", TOPIC, "eventType", "PaymentApproved").count()).isEqualTo(1.0);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, BusinessEvent<?>> captured() {
        ArgumentCaptor<ProducerRecord<String, BusinessEvent<?>>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }

    static PaymentApproved approvedV2(String correlationId) {
        return new PaymentApproved(PaymentApproved.CURRENT_VERSION, UUID.randomUUID(), Instant.parse("2026-11-13T10:00:00Z"),
            correlationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "c@x.test", UUID.randomUUID(),
            "Java mentoring", UUID.randomUUID(), Instant.parse("2026-12-01T15:00:00Z"), 2, MentorshipMode.LIVE);
    }
}
