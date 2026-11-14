package com.riwi.skillbridge.application.port.out;

import java.util.UUID;

/**
 * Registro de eventos ya procesados por grupo consumidor (entrega at-least-once de Kafka).
 * Puerto de salida (HU-104): lo implementa el adaptador de persistencia (frontera hexagonal, CLAUDE.md).
 */
public interface ProcessedEventStorePort {

    /**
     * Marca el evento como procesado por {@code consumerGroup}.
     *
     * @return {@code true} si es la primera vez; {@code false} si ya estaba registrado (duplicado)
     */
    boolean markProcessed(String consumerGroup, UUID eventId, String eventType);
}
