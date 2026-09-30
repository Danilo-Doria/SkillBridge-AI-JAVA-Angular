package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offerings")
public class OfferingEntity {
    @Id
    private UUID id;
    private String title;
    private String description;
    private String category;
    private BigDecimal price;
    private boolean active;
    @Column(name = "created_at")
    private Instant createdAt;

    protected OfferingEntity() {}

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
}
