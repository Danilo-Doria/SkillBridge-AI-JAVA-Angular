package com.riwi.skillbridge.payment.application.service;

import com.riwi.skillbridge.payment.application.port.in.CheckUserAccessUseCase;
import com.riwi.skillbridge.payment.application.port.in.SyncUserAccessUseCase;
import com.riwi.skillbridge.payment.application.port.out.UserAccessRepositoryPort;
import com.riwi.skillbridge.payment.domain.model.UserAccess;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserAccessService implements SyncUserAccessUseCase, CheckUserAccessUseCase {

    private final UserAccessRepositoryPort repository;

    public UserAccessService(UserAccessRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public boolean apply(UserAccess access, UUID eventId, Instant occurredAt) {
        Objects.requireNonNull(access, "access es obligatorio");
        Objects.requireNonNull(eventId, "eventId es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
        return repository.upsert(access, eventId, occurredAt);
    }

    @Override
    public boolean isAllowed(UUID userId, String tokenRole) {
        return repository.findById(userId).map(access -> access.permits(tokenRole)).orElse(true);
    }
}
