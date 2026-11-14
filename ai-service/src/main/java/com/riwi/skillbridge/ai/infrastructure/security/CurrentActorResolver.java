package com.riwi.skillbridge.ai.infrastructure.security;

import com.riwi.skillbridge.ai.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.ai.domain.model.Actor;
import com.riwi.skillbridge.ai.domain.model.Role;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Construye el {@link Actor} desde las claims del JWT ({@code uid}, {@code role}, {@code sub}); sin tabla users. */
@Component
public class CurrentActorResolver {

    public Actor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ForbiddenOperationException("Usuario no autenticado");
        }
        Role role;
        try {
            role = Role.valueOf(user.role());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ForbiddenOperationException("Usuario sin rol");
        }
        return new Actor(user.id(), user.email(), role);
    }
}
