package com.riwi.skillbridge.ai.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OfferingViewJpaRepository extends JpaRepository<OfferingViewEntity, UUID> {

    List<OfferingViewEntity> findByActiveTrueOrderByTitleAsc();

    /**
     * Upsert idempotente por {@code id}: solo actualiza si el evento es igual o más reciente que el último aplicado
     * y no es el mismo {@code eventId} (entrega at-least-once de Kafka).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO ai.offering_view (id, provider_id, title, description, category, price, active, mode,
                                          duration_hours, last_event_id, source_occurred_at, updated_at)
            VALUES (:id, :providerId, :title, :description, :category, :price, :active, :mode,
                    :durationHours, :eventId, :occurredAt, now())
            ON CONFLICT (id) DO UPDATE SET
                provider_id = EXCLUDED.provider_id,
                title = EXCLUDED.title,
                description = EXCLUDED.description,
                category = EXCLUDED.category,
                price = EXCLUDED.price,
                active = EXCLUDED.active,
                mode = EXCLUDED.mode,
                duration_hours = EXCLUDED.duration_hours,
                last_event_id = EXCLUDED.last_event_id,
                source_occurred_at = EXCLUDED.source_occurred_at,
                updated_at = now()
            WHERE ai.offering_view.source_occurred_at <= EXCLUDED.source_occurred_at
              AND ai.offering_view.last_event_id IS DISTINCT FROM EXCLUDED.last_event_id
            """, nativeQuery = true)
    int upsert(@Param("id") UUID id,
               @Param("providerId") UUID providerId,
               @Param("title") String title,
               @Param("description") String description,
               @Param("category") String category,
               @Param("price") BigDecimal price,
               @Param("active") boolean active,
               @Param("mode") String mode,
               @Param("durationHours") int durationHours,
               @Param("eventId") UUID eventId,
               @Param("occurredAt") Instant occurredAt);
}
