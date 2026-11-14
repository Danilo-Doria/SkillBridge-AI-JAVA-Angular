package com.riwi.skillbridge.payment.application.common;

import java.util.UUID;

public final class CorrelationIdHolder {
    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    private CorrelationIdHolder() {
    }

    public static void set(String correlationId) {
        holder.set(correlationId);
    }

    public static String get() {
        return holder.get();
    }

    /** Correlación vigente o una nueva si no hay. */
    public static String currentOrNew() {
        String value = holder.get();
        return value != null && !value.isBlank() ? value : UUID.randomUUID().toString();
    }

    public static void clear() {
        holder.remove();
    }
}
