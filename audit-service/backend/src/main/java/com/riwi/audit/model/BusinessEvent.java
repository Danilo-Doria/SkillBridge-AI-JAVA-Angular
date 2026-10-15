package com.riwi.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Documento de auditoría persistido en MongoDB (colección {@code audit_events}).
 * Mismo contrato de campos que antes (cuando vivía solo en memoria), para no
 * romper el JSON que ya consume {@code audit-frontend}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "audit_events")
public class BusinessEvent {
    @Id
    private UUID eventId;
    @Indexed
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    @Indexed(name = "occurredAt_idx")
    private Instant occurredAt;
    private String correlationId;
    private int version;
    private Map<String, Object> payload; // using Map to dynamically read any payload
}
