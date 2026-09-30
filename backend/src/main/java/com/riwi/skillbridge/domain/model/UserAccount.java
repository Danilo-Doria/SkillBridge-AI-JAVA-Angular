package com.riwi.skillbridge.domain.model;

import java.util.UUID;

public record UserAccount(UUID id, String name, String email, String passwordHash, Role role) {}
