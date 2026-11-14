package com.riwi.skillbridge.ai.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code ai.offering_view}, proyección local de las ofertas del monolito. */
@Entity
@Table(name = "offering_view", schema = "ai")
public class OfferingViewEntity {

    @Id
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 120)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours;

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Column(name = "source_occurred_at", nullable = false)
    private Instant sourceOccurredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OfferingViewEntity() {
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public String getMode() { return mode; }
    public int getDurationHours() { return durationHours; }
    public UUID getLastEventId() { return lastEventId; }
    public Instant getSourceOccurredAt() { return sourceOccurredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
