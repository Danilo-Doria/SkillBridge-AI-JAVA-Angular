package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mentorship_resources")
public class MentorshipResourceEntity {
    @Id private UUID id;
    @Column(name = "offering_id", nullable = false) private UUID offeringId;
    @Column(nullable = false) private String title;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(nullable = false, length = 1500) private String url;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "file_data") private byte[] fileData;
    @Column(name = "file_name") private String fileName;

    public String getFileName() { return fileName; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public byte[] getFileData() { return fileData; }
    public void update(String title, String resourceType, String url) {
        this.title = title; this.resourceType = resourceType; this.url = url;
        if (!"DOCUMENT".equals(resourceType) || !url.equals("/api/mentorship-resources/" + id + "/file")) {
            fileData = null; fileName = null;
        }
    }
    public void attach(byte[] data, String name) {
        fileData = data; fileName = name; resourceType = "DOCUMENT";
        url = "/api/mentorship-resources/" + id + "/file";
    }

    protected MentorshipResourceEntity() { }
    public MentorshipResourceEntity(UUID id, UUID offeringId, String title, String resourceType, String url) {
        this.id = id; this.offeringId = offeringId; this.title = title; this.resourceType = resourceType; this.url = url; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getOfferingId() { return offeringId; }
    public String getTitle() { return title; }
    public String getResourceType() { return resourceType; }
    public String getUrl() { return url; }
}
