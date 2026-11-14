package com.riwi.skillbridge.ai.application.port.out;

import com.riwi.skillbridge.ai.domain.model.Offering;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model local de ofertas ({@code ai.offering_view}). ai-service nunca consulta la BD del monolito:
 * la vista se alimenta con eventos {@code OfferingUpserted} del topic {@code booking-events}.
 */
public interface OfferingRepositoryPort {

    /** Ofertas activas para el catálogo que recibe el LLM. */
    List<Offering> findAllActive();

    Optional<Offering> findById(UUID id);

    /**
     * Inserta o actualiza la oferta. Idempotente: un evento repetido o más antiguo que el último aplicado
     * ({@code occurredAt} menor) no modifica la fila.
     *
     * @return {@code true} si la fila cambió
     */
    boolean upsert(Offering offering, UUID eventId, Instant occurredAt);
}
