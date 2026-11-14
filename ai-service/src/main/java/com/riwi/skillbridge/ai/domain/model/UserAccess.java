package com.riwi.skillbridge.ai.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Último estado de acceso conocido de un usuario del monolito (proyección de {@code UserAccessChanged}).
 * Un token solo autentica si el usuario está {@link AccessStatus#ACTIVE} y su rol coincide con el claim del token.
 */
public record UserAccess(UUID userId, AccessStatus status, String role) {

    public UserAccess {
        Objects.requireNonNull(userId, "userId es obligatorio");
        Objects.requireNonNull(status, "status es obligatorio");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role es obligatorio");
        }
    }

    /** {@code true} si un token con el rol {@code tokenRole} puede seguir autenticando a este usuario. */
    public boolean permits(String tokenRole) {
        return status == AccessStatus.ACTIVE && role.equals(tokenRole);
    }
}
