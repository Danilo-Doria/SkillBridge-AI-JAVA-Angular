package com.riwi.skillbridge.ai.application.port.in;

import java.util.UUID;

/** Decide si un JWT ya validado (firma, emisor, expiración) puede autenticar a su usuario. */
public interface CheckUserAccessUseCase {
    /**
     * {@code false} si el usuario está suspendido o su rol actual difiere del claim {@code role} del token.
     * Sin estado conocido para el usuario: {@code true} (activo con el rol del token).
     */
    boolean isAllowed(UUID userId, String tokenRole);
}
