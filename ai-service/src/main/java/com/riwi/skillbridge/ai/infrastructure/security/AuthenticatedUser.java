package com.riwi.skillbridge.ai.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

/**
 * Principal construido solo con las claims del JWT ({@code uid}, {@code sub}=email, {@code role}).
 * ai-service no tiene tabla de usuarios.
 */
public record AuthenticatedUser(UUID id, String email, String role) implements Principal {
    @Override
    public String getName() {
        return email;
    }
}
