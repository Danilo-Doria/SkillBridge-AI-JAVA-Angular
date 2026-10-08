package com.riwi.skillbridge.domain.model;

import java.util.UUID;

public record UserAccount(UUID id, String name, String email, String passwordHash, Role role, UserStatus status) {

    // Cuentas nuevas (registro) nacen activas; así AuthService y los tests no cambian.
    public UserAccount(UUID id, String name, String email, String passwordHash, Role role) {
        this(id, name, email, passwordHash, role, UserStatus.ACTIVE);
    }
}
