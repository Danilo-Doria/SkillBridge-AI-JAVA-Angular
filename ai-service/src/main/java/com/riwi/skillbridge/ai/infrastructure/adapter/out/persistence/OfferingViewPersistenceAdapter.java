package com.riwi.skillbridge.ai.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.ai.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.ai.domain.model.MentorshipMode;
import com.riwi.skillbridge.ai.domain.model.Offering;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OfferingViewPersistenceAdapter implements OfferingRepositoryPort {

    private final OfferingViewJpaRepository repository;

    public OfferingViewPersistenceAdapter(OfferingViewJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Offering> findAllActive() {
        return repository.findByActiveTrueOrderByTitleAsc().stream().map(OfferingViewPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Offering> findById(UUID id) {
        return repository.findById(id).map(OfferingViewPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public boolean upsert(Offering o, UUID eventId, Instant occurredAt) {
        return repository.upsert(o.id(), o.providerId(), o.title(), o.description(), o.category(), o.price(),
                o.active(), o.mode().name(), o.durationHours(), eventId, occurredAt) > 0;
    }

    static Offering toDomain(OfferingViewEntity e) {
        MentorshipMode mode;
        try {
            mode = e.getMode() == null ? null : MentorshipMode.valueOf(e.getMode());
        } catch (IllegalArgumentException ex) {
            mode = null; // valor desconocido: el dominio lo normaliza a ASYNCHRONOUS
        }
        return new Offering(e.getId(), e.getProviderId(), e.getTitle(), e.getDescription(), e.getCategory(),
                e.getPrice(), e.isActive(), mode, e.getDurationHours());
    }
}
