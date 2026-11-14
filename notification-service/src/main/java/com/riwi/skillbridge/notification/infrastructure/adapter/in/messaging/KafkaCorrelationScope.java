package com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Restaura la correlación de un registro Kafka en MDC mientras se procesa y deja el hilo del listener como
 * estaba al cerrar. Prioridad (igual que el monolito): cabecera {@code X-Correlation-Id}, después
 * {@code correlationId} del envelope y, si faltan ambos, uno nuevo.
 */
public final class KafkaCorrelationScope implements AutoCloseable {

    private final String correlationId;
    private final String previousMdc;

    private KafkaCorrelationScope(String correlationId) {
        this.correlationId = correlationId;
        this.previousMdc = MDC.get(KafkaEventHeaders.MDC_CORRELATION_ID);
        MDC.put(KafkaEventHeaders.MDC_CORRELATION_ID, correlationId);
    }

    public static KafkaCorrelationScope open(ConsumerRecord<?, ? extends BusinessEventEnvelope> record) {
        String fromHeader = KafkaEventHeaders.read(record.headers(), KafkaEventHeaders.CORRELATION_ID);
        String fromEnvelope = record.value() != null ? record.value().correlationId() : null;
        String resolved = notBlank(fromHeader) ? fromHeader
            : notBlank(fromEnvelope) ? fromEnvelope
            : UUID.randomUUID().toString();
        return new KafkaCorrelationScope(resolved);
    }

    public String correlationId() {
        return correlationId;
    }

    @Override
    public void close() {
        if (previousMdc != null) MDC.put(KafkaEventHeaders.MDC_CORRELATION_ID, previousMdc);
        else MDC.remove(KafkaEventHeaders.MDC_CORRELATION_ID);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
