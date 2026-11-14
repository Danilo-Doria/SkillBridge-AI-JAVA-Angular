package com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Cabeceras del contrato {@code specs/104-microservices-gateway/contracts/kafka-topics.md}.
 * Mismos nombres que {@code KafkaEventHeaders} del monolito (copia, sin dependencia de compilación).
 */
public final class KafkaEventHeaders {

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String DLT_SUFFIX = ".DLT";

    /** Valores de {@code eventType} que publica {@code KafkaNotificationPublisher#eventTypeFor}. */
    public static final Set<String> NOTIFICATION_EVENT_TYPES = Set.of(
        "BookingCreatedNotification", "BookingCancelledNotification", "PaymentReminderNotification");

    private KafkaEventHeaders() {
    }

    public static String read(Headers headers, String name) {
        if (headers == null) return null;
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
