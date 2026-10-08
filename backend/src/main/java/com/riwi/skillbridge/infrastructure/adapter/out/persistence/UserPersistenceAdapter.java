package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort, UserAccountPort {
    private final JpaUserRepository repository;

    public UserPersistenceAdapter(JpaUserRepository repository) { this.repository = repository; }

    @Override
    public boolean existsByEmail(String email) { return repository.existsByEmailIgnoreCase(email); }

    @Override
    public Optional<UserAccount> findByEmail(String email) { return repository.findByEmailIgnoreCase(email).map(this::toDomain); }

    @Override
    public UserAccount save(UserAccount user) {
        // Conserva la fecha de creación original: antes se reiniciaba en cada guardado.
        Instant createdAt = repository.findById(user.id())
            .map(UserEntity::getCreatedAt)
            .orElseGet(Instant::now);
        UserEntity saved = repository.save(new UserEntity(
            user.id(), user.name(), user.email(), user.passwordHash(),
            user.role(), user.status(), createdAt));
        return toDomain(saved);
    }

    private UserAccount toDomain(UserEntity e) {
        return new UserAccount(e.getId(), e.getName(), e.getEmail(), e.getPassword(), e.getRole(), e.getStatus());
    }

    @Override
    public Optional<UUID> findIdByEmail(String email) { return findByEmail(email).map(UserAccount::id); }
}
