package com.riwi.skillbridge.ai.application.port.in;

import com.riwi.skillbridge.ai.domain.model.Offering;

import java.time.Instant;
import java.util.UUID;

/** Proyecta un evento {@code OfferingUpserted} sobre el read model de ofertas. */
public interface SyncOfferingViewUseCase {
    boolean apply(Offering offering, UUID eventId, Instant occurredAt);
}
