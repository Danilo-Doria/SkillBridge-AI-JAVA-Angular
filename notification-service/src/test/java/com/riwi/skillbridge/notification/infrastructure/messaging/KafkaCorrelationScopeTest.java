package com.riwi.skillbridge.notification.infrastructure.messaging;

import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.BusinessEventEnvelope;
import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.KafkaCorrelationScope;
import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.KafkaEventHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaCorrelationScopeTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    private ConsumerRecord<String, BusinessEventEnvelope> record(String header, String envelopeCorrelation) {
        BusinessEventEnvelope envelope = new BusinessEventEnvelope(UUID.randomUUID(), "BookingCreatedNotification",
            "b", "Booking", Instant.now(), envelopeCorrelation, 1, null);
        ConsumerRecord<String, BusinessEventEnvelope> record =
            new ConsumerRecord<>("notification-requests", 0, 0L, "b", envelope);
        if (header != null) {
            record.headers().add(KafkaEventHeaders.CORRELATION_ID, header.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    void headerWinsAndPreviousMdcIsRestored() {
        MDC.put("correlationId", "outer");
        try (KafkaCorrelationScope scope = KafkaCorrelationScope.open(record("from-header", "from-envelope"))) {
            assertThat(scope.correlationId()).isEqualTo("from-header");
            assertThat(MDC.get("correlationId")).isEqualTo("from-header");
        }
        assertThat(MDC.get("correlationId")).isEqualTo("outer");
    }

    @Test
    void fallsBackToEnvelopeThenGenerated() {
        try (KafkaCorrelationScope scope = KafkaCorrelationScope.open(record(null, "from-envelope"))) {
            assertThat(MDC.get("correlationId")).isEqualTo("from-envelope");
        }
        assertThat(MDC.get("correlationId")).isNull();

        try (KafkaCorrelationScope scope = KafkaCorrelationScope.open(record(" ", null))) {
            assertThat(UUID.fromString(scope.correlationId())).isNotNull();
        }
    }
}
