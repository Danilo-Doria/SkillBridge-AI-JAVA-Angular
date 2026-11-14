package com.riwi.skillbridge.ai.application.service;

import com.riwi.skillbridge.ai.application.port.in.SyncOfferingViewUseCase;
import com.riwi.skillbridge.ai.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.ai.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class OfferingViewService implements SyncOfferingViewUseCase {

    private final OfferingRepositoryPort offerings;

    public OfferingViewService(OfferingRepositoryPort offerings) {
        this.offerings = offerings;
    }

    @Override
    public boolean apply(Offering offering, UUID eventId, Instant occurredAt) {
        Objects.requireNonNull(offering, "offering es obligatorio");
        Objects.requireNonNull(offering.id(), "offering.id es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
        return offerings.upsert(offering, eventId, occurredAt);
    }
}
