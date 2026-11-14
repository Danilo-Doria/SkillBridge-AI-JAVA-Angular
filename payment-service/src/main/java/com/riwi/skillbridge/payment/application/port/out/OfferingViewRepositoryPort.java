package com.riwi.skillbridge.payment.application.port.out;

import com.riwi.skillbridge.payment.domain.model.OfferingView;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OfferingViewRepositoryPort {

    Optional<OfferingView> findById(UUID offeringId);

    /** Upsert condicionado a que {@code occurredAt} sea posterior al último evento aplicado. */
    boolean upsert(OfferingView offering, UUID eventId, Instant occurredAt);
}
