package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Cabeceras Kafka del contrato {@code specs/104-microservices-gateway/contracts/kafka-topics.md}:
 * {@code X-Correlation-Id}, {@code eventId}, {@code eventType}, {@code schemaVersion}.
 * El mismo valor de correlación viaja también dentro del envelope {@link BusinessEvent}.
 */
public final class KafkaEventHeaders {

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String DLT_SUFFIX = ".DLT";

    private KafkaEventHeaders() {
    }

    /** Correlación vigente en el hilo (petición HTTP o consumo); si no hay, una nueva. */
    public static String currentCorrelationId(String preferred) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        String fromHolder = CorrelationIdHolder.get();
        if (fromHolder != null && !fromHolder.isBlank()) return fromHolder;
        String fromMdc = MDC.get(MDC_CORRELATION_ID);
        if (fromMdc != null && !fromMdc.isBlank()) return fromMdc;
        return UUID.randomUUID().toString();
    }

    /** Registro con clave = aggregateId (bookingId) y las cabeceras del contrato. */
    public static ProducerRecord<String, BusinessEvent<?>> record(String topic, BusinessEvent<?> event) {
        ProducerRecord<String, BusinessEvent<?>> record = new ProducerRecord<>(topic, event.aggregateId(), event);
        Headers headers = record.headers();
        put(headers, CORRELATION_ID, event.correlationId());
        put(headers, EVENT_ID, event.eventId().toString());
        put(headers, EVENT_TYPE, event.eventType());
        put(headers, SCHEMA_VERSION, Integer.toString(event.version()));
        return record;
    }

    public static String read(Headers headers, String name) {
        if (headers == null) return null;
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void put(Headers headers, String name, String value) {
        if (value != null) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
