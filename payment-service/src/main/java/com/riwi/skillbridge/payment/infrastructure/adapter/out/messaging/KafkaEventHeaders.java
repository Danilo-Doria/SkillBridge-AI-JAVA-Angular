package com.riwi.skillbridge.payment.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.payment.application.port.out.event.BusinessEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

/**
 * Cabeceras del contrato {@code specs/104-microservices-gateway/contracts/kafka-topics.md} (iguales que
 * {@code KafkaEventHeaders} del monolito) más {@code __TypeId__} con el nombre de clase del monolito, para que los
 * consumidores con {@code JsonDeserializer} que resuelven el tipo por cabecera (audit/analytics) lo entiendan.
 */
public final class KafkaEventHeaders {

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String TYPE_ID = "__TypeId__";

    private KafkaEventHeaders() {
    }

    /** Registro con clave = aggregateId (bookingId) y las cabeceras del contrato. */
    public static ProducerRecord<String, BusinessEvent<?>> record(String topic, BusinessEvent<?> event, String typeId) {
        ProducerRecord<String, BusinessEvent<?>> record = new ProducerRecord<>(topic, event.aggregateId(), event);
        Headers headers = record.headers();
        put(headers, TYPE_ID, typeId);
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
        if (value != null && !value.isBlank()) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
