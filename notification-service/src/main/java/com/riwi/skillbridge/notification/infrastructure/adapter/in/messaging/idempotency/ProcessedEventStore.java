package com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.idempotency;

import java.util.UUID;

/**
 * Registro de eventos ya procesados por grupo consumidor (entrega at-least-once de Kafka).
 * Detalle de entrega del adaptador de entrada: no es un puerto de negocio.
 */
public interface ProcessedEventStore {

    /** @return {@code true} si es la primera vez; {@code false} si ya estaba registrado (duplicado). */
    boolean markProcessed(String consumerGroup, UUID eventId, String eventType);
}
