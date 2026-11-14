package com.riwi.skillbridge.notification.domain.model;

/** Destinatario de un correo. {@code name} puede faltar; {@code email} no. */
public record Recipient(String email, String name) {

    public Recipient {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email es obligatorio");
        }
    }

    public String displayName() {
        return name == null || name.isBlank() ? email : name;
    }
}
