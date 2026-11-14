package com.riwi.skillbridge.ai.application.port.in;

import com.riwi.skillbridge.ai.domain.model.UserAccess;

import java.time.Instant;
import java.util.UUID;

/** Proyecta un evento {@code UserAccessChanged} sobre {@code ai.user_access}. */
public interface SyncUserAccessUseCase {
    /** @return {@code true} si la fila cambió (un evento repetido o más antiguo no la cambia) */
    boolean apply(UserAccess access, UUID eventId, Instant occurredAt);
}
