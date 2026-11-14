package com.riwi.skillbridge.infrastructure.adapter.in.messaging.idempotency;

import com.riwi.skillbridge.application.port.out.ProcessedEventStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

/**
 * Ejecuta el efecto de un evento como máximo una vez por {@code (consumerGroup, eventId)}.
 *
 * <p>La marca y el efecto comparten transacción: si el efecto falla, la marca se revierte y el
 * reintento (o la DLT) ve el evento como no procesado. Los efectos externos no transaccionales
 * (p. ej. enviar un email) siguen siendo at-least-once si falla justo el commit.
 */
@Component
public class IdempotentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

    private final ProcessedEventStorePort store;
    private final TransactionOperations transactions;

    public IdempotentEventProcessor(ProcessedEventStorePort store, TransactionOperations transactions) {
        this.store = store;
        this.transactions = transactions;
    }

    /** @return {@code true} si se ejecutó el efecto; {@code false} si era un duplicado. */
    public boolean processOnce(String consumerGroup, UUID eventId, String eventType, Runnable effect) {
        Boolean executed = transactions.execute(status -> {
            if (!store.markProcessed(consumerGroup, eventId, eventType)) {
                log.warn("Duplicate event skipped consumerGroup={} eventId={} eventType={}",
                    consumerGroup, eventId, eventType);
                return false;
            }
            effect.run();
            return true;
        });
        return Boolean.TRUE.equals(executed);
    }
}
