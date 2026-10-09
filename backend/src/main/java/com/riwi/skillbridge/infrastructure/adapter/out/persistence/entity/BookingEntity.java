package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.BookingStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class BookingEntity {
    @Id
    private UUID id;
    @Column(name = "offering_id")
    private UUID offeringId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    @Enumerated(EnumType.STRING)
    private BookingStatus status;
    @Version
    private long version;
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected BookingEntity() {}

    public BookingEntity(UUID id, UUID offeringId, UUID customerId, Instant scheduledAt, BookingStatus status, long version, Instant createdAt) {
        this.id = id;
        this.offeringId = offeringId;
        this.customerId = customerId;
        this.scheduledAt = scheduledAt;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOfferingId() { return offeringId; }
    public UUID getCustomerId() { return customerId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public BookingStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateStatus(BookingStatus status) {
        this.status = status;
    }
}
