package com.riwi.skillbridge.payment.application.port.out;

import com.riwi.skillbridge.payment.domain.model.UserAccess;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Estado de acceso local ({@code payment.user_access}), alimentado por {@code UserAccessChanged} de {@code booking-events}. */
public interface UserAccessRepositoryPort {

    /** Consulta por clave primaria (se ejecuta en cada petición autenticada). */
    Optional<UserAccess> findById(UUID userId);

    /**
     * Inserta o actualiza. Idempotente: solo aplica si {@code occurredAt} es igual o posterior al último aplicado y el
     * {@code eventId} no es el último aplicado.
     *
     * @return {@code true} si la fila cambió
     */
    boolean upsert(UserAccess access, UUID eventId, Instant occurredAt);
}
