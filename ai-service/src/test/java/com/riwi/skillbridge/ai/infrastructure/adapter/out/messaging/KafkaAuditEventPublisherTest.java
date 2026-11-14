package com.riwi.skillbridge.ai.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.ai.application.port.out.event.BusinessEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaAuditEventPublisherTest {

    private static final String MONOLITH_TYPE = "com.riwi.skillbridge.application.port.out.event.BusinessEvent";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, BusinessEvent<?>> template = mock(KafkaTemplate.class);
    private final KafkaAuditEventPublisher publisher =
            new KafkaAuditEventPublisher(template, "booking-events", "recommendation-events", MONOLITH_TYPE);

    private static BusinessEvent<Map<String, Object>> event(UUID eventId, String type, int version) {
        return new BusinessEvent<>(eventId, type, "agg-1", "Recommendation", Instant.now(), "corr-1", version,
                Map.of("k", "v"), null, null, null, "GENERATE", "RECOMMENDATION", "agg-1");
    }

    @Test
    void recordUsesAggregateIdKeyAndMonolithTypeHeader() {
        UUID eventId = UUID.randomUUID();
        ProducerRecord<String, BusinessEvent<?>> record =
                publisher.toRecord("booking-events", event(eventId, "RecommendationGenerated", 2));

        assertThat(record.topic()).isEqualTo("booking-events");
        assertThat(record.key()).isEqualTo("agg-1");
        assertHeaders(record, eventId, "RecommendationGenerated", "2");
    }

    @Test
    void recommendationGeneratedGoesToBookingAndRecommendationEventsWithSameEnvelopeAndHeaders() {
        UUID eventId = UUID.randomUUID();
        BusinessEvent<?> generated = event(eventId, "RecommendationGenerated", 2);

        List<ProducerRecord<String, BusinessEvent<?>>> records = publisher.toRecords(generated);

        assertThat(records).extracting(ProducerRecord::topic).containsExactly("booking-events", "recommendation-events");
        for (ProducerRecord<String, BusinessEvent<?>> record : records) {
            assertThat(record.value()).isSameAs(generated);
            assertThat(record.key()).isEqualTo("agg-1");
            assertHeaders(record, eventId, "RecommendationGenerated", "2");
        }
    }

    @Test
    void recommendationRequestedGoesOnlyToBookingEvents() {
        assertThat(publisher.toRecords(event(UUID.randomUUID(), "RecommendationRequested", 1)))
                .extracting(ProducerRecord::topic).containsExactly("booking-events");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishSendsGeneratedToBothTopicsAndRequestedOnlyToBookingEvents() {
        when(template.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

        publisher.publish(event(UUID.randomUUID(), "RecommendationGenerated", 2));
        ArgumentCaptor<ProducerRecord<String, BusinessEvent<?>>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template, timeout(5_000).times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(ProducerRecord::topic)
                .containsExactlyInAnyOrder("booking-events", "recommendation-events");

        publisher.publish(event(UUID.randomUUID(), "RecommendationRequested", 1));
        verify(template, after(500).times(3)).send(sent.capture());
        assertThat(sent.getAllValues().get(sent.getAllValues().size() - 1).topic()).isEqualTo("booking-events");
    }

    private static void assertHeaders(ProducerRecord<?, ?> record, UUID eventId, String type, String version) {
        assertThat(header(record, "__TypeId__")).isEqualTo(MONOLITH_TYPE);
        assertThat(header(record, "X-Correlation-Id")).isEqualTo("corr-1");
        assertThat(header(record, "eventId")).isEqualTo(eventId.toString());
        assertThat(header(record, "eventType")).isEqualTo(type);
        assertThat(header(record, "schemaVersion")).isEqualTo(version);
    }

    private static String header(ProducerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
