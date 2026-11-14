package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ActivateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.CreateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.DeactivateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.PublishOfferingSnapshotUseCase;
import com.riwi.skillbridge.application.port.in.UpdateOfferingUseCase;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.OfferingUpsertedPayload;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.service.knowledge.KnowledgeIndexService;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.policy.OfferingAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OfferingCommandService implements CreateOfferingUseCase, UpdateOfferingUseCase,
    DeactivateOfferingUseCase, ActivateOfferingUseCase, PublishOfferingSnapshotUseCase {

    static final String OFFERING_UPSERTED = "OfferingUpserted";

    private final OfferingRepositoryPort repository;
    private final OfferingCachePort cache;
    private final OfferingAccessPolicy policy;
    private final AuditEventPublisherPort auditPublisher;
    private final KnowledgeIndexService knowledgeIndexService;
    /** HU-104: OfferingUpserted es un evento de read model y va SIEMPRE a booking-events (no a audit-events). */
    private final BookingEventPublisherPort catalogPublisher;

    @Autowired
    public OfferingCommandService(OfferingRepositoryPort repository,
                                  OfferingCachePort cache,
                                  OfferingAccessPolicy policy,
                                  AuditEventPublisherPort auditPublisher,
                                  @Autowired(required = false) KnowledgeIndexService knowledgeIndexService,
                                  @Autowired(required = false) BookingEventPublisherPort catalogPublisher) {
        this.repository = repository;
        this.cache = cache;
        this.policy = policy;
        this.auditPublisher = auditPublisher;
        this.knowledgeIndexService = knowledgeIndexService;
        this.catalogPublisher = catalogPublisher;
    }

    public OfferingCommandService(OfferingRepositoryPort repository,
                                  OfferingCachePort cache,
                                  OfferingAccessPolicy policy,
                                  AuditEventPublisherPort auditPublisher,
                                  KnowledgeIndexService knowledgeIndexService) {
        this(repository, cache, policy, auditPublisher, knowledgeIndexService, null);
    }

    public OfferingCommandService(OfferingRepositoryPort repository,
                                  OfferingCachePort cache,
                                  OfferingAccessPolicy policy,
                                  AuditEventPublisherPort auditPublisher) {
        this(repository, cache, policy, auditPublisher, null, null);
    }

    @Override
    public Offering create(Actor actor, CreateOfferingUseCase.Command command) {
        UUID ownerId = resolveOwner(actor, command.providerId());
        Offering saved = repository.save(Offering.create(ownerId, command.title(),
            command.description(), command.category(), command.price(),
            command.mode(), command.durationHours()));
        cache.evictActiveOfferings();
        if (knowledgeIndexService != null) {
            knowledgeIndexService.reindexOffering(saved);
        }
        
        publishAudit("OfferingCreated", actor, "CREATE", "OFFERING", saved.id().toString());
        publishUpserted(saved, actor);
        return saved;
    }

    @Override
    public Offering update(Actor actor, UUID offeringId, UpdateOfferingUseCase.Command command) {
        Offering offering = load(offeringId);
        policy.assertCanModify(actor, offering);
        Offering saved = repository.save(offering.update(command.title(),
            command.description(), command.category(), command.price(),
            command.mode(), command.durationHours()));
        cache.evictActiveOfferings();
        if (knowledgeIndexService != null) {
            knowledgeIndexService.reindexOffering(saved);
        }
        
        publishAudit("OfferingUpdated", actor, "UPDATE", "OFFERING", saved.id().toString());
        publishUpserted(saved, actor);
        return saved;
    }

    @Override
    public void deactivate(Actor actor, UUID offeringId) {
        Offering offering = load(offeringId);
        policy.assertCanModify(actor, offering);
        if (!offering.active()) return;          // idempotente
        Offering saved = repository.save(offering.deactivate());
        cache.evictActiveOfferings();
        if (knowledgeIndexService != null) {
            knowledgeIndexService.reindexOffering(saved);
        }
        
        publishAudit("OfferingDeactivated", actor, "DEACTIVATE", "OFFERING", offering.id().toString());
        publishUpserted(saved, actor);
    }

    @Override
    public void activate(Actor actor, UUID offeringId) {
        Offering offering = load(offeringId);
        policy.assertCanModify(actor, offering);
        if (offering.active()) return;           // idempotente
        Offering saved = repository.save(offering.activate());
        cache.evictActiveOfferings();
        if (knowledgeIndexService != null) {
            knowledgeIndexService.reindexOffering(saved);
        }
        
        publishAudit("OfferingActivated", actor, "ACTIVATE", "OFFERING", offering.id().toString());
        publishUpserted(saved, actor);
    }

    @Override
    public int publishActiveSnapshot() {
        // HU-104: TODAS las ofertas (también inactivas): una desactivación más antigua que la retención de Kafka
        // debe llegar igual a un read model reconstruido; si no, seguiría recomendando ofertas desactivadas.
        var all = repository.findAll();
        all.forEach(o -> publishUpserted(o, null));
        return all.size();
    }

    /** ECST para read models externos (HU-104). Sin actor cuando lo emite el sistema (snapshot). */
    private void publishUpserted(Offering offering, Actor actor) {
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        String id = offering.id().toString();
        BusinessEvent<OfferingUpsertedPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), OFFERING_UPSERTED, id, "Offering", Instant.now(), correlationId, 1,
            OfferingUpsertedPayload.from(offering),
            actor == null ? null : actor.id().toString(),
            actor == null ? null : actor.username(),
            actor == null ? null : actor.role().name(),
            "UPSERT", "OFFERING", id
        );
        if (catalogPublisher != null) {
            catalogPublisher.publish(event);
        } else {
            auditPublisher.publish(event); // solo tests sin cableado completo (mismo topic por defecto)
        }
    }

    private void publishAudit(String eventType, Actor actor, String action, String resource, String resourceId) {
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BusinessEvent<String> event = new BusinessEvent<>(
            UUID.randomUUID(), eventType, resourceId, resource, Instant.now(), correlationId, 1, eventType,
            actor.id().toString(), actor.username(), actor.role().name(), action, resource, resourceId
        );
        auditPublisher.publish(event);
    }

    private Offering load(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Offering no encontrado"));
    }

    private UUID resolveOwner(Actor actor, UUID requestedProviderId) {
        return switch (actor.role()) {
            case PROVIDER -> actor.id();   // se ignora lo que venga en la petición
            case ADMIN -> {
                if (requestedProviderId == null) {
                    throw new IllegalArgumentException("providerId es obligatorio para Admin");
                }
                yield requestedProviderId;
            }
            case CUSTOMER -> throw new ForbiddenOperationException("No tienes permiso para crear offerings");
        };
    }
}
