package com.riwi.skillbridge.ai.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.ai.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.ai.application.port.out.event.BusinessEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Publica {@code RecommendationRequested}/{@code RecommendationGenerated} en {@code booking-events} con clave
 * {@code aggregateId}, igual que {@code KafkaAuditEventPublisher} del monolito (asíncrono; un fallo de Kafka no
 * rompe la respuesta HTTP).
 *
 * <p>Compatibilidad: los consumidores del monolito ({@code KafkaAnalyticsConsumer}, {@code KafkaAuditConsumer}) usan
 * {@code JsonDeserializer} sin tipo por defecto y resuelven la clase con la cabecera {@code __TypeId__}. Por eso la
 * cabecera se fija explícitamente al nombre de clase del monolito (configurable) en lugar de la clase local.
 * Se añaden además las cabeceras del contrato ({@code X-Correlation-Id}, {@code eventId}, {@code eventType},
 * {@code schemaVersion}).
 *
 * <p>HU-104 (hallazgo R1): {@code RecommendationGenerated} se publica ADEMÁS en {@code recommendation-events}
 * ({@code app.kafka.topic.recommendation-events}), el topic que escucha {@code KafkaAnalyticsConsumer} del monolito
 * (trending). Es el mismo envelope, con el mismo {@code eventId} y las mismas cabeceras (analytics es idempotente por
 * {@code eventId}). {@code RecommendationRequested} solo va a {@code booking-events}.
 */
@Component
public class KafkaAuditEventPublisher implements AuditEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditEventPublisher.class);

    private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate;
    static final String RECOMMENDATION_GENERATED = "RecommendationGenerated";

    private final String topic;
    private final String recommendationTopic;
    private final String typeIdHeader;

    public KafkaAuditEventPublisher(KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate,
                                    @Value("${app.kafka.topic.audit-events}") String topic,
                                    @Value("${app.kafka.topic.recommendation-events:recommendation-events}")
                                    String recommendationTopic,
                                    @Value("${app.kafka.type-id-header:com.riwi.skillbridge.application.port.out.event.BusinessEvent}")
                                    String typeIdHeader) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.recommendationTopic = recommendationTopic;
        this.typeIdHeader = typeIdHeader;
    }

    @Override
    public void publish(BusinessEvent<?> event) {
        List<ProducerRecord<String, BusinessEvent<?>>> records = toRecords(event);
        CompletableFuture.runAsync(() -> {
            log.info("Publicando evento en Kafka - eventId: {}, eventType: {}, aggregateId: {}, correlationId: {}",
                    event.eventId(), event.eventType(), event.aggregateId(), event.correlationId());
            for (ProducerRecord<String, BusinessEvent<?>> record : records) {
                try {
                    kafkaTemplate.send(record)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.info("Evento publicado en el topic {}: {}", record.topic(), event.eventId());
                            } else {
                                log.error("Error publicando evento en el topic {}: {}", record.topic(), event.eventId(), ex);
                            }
                        });
                } catch (Exception e) {
                    log.error("Kafka inaccesible (topic {}): {}", record.topic(), e.getMessage());
                }
            }
        });
    }

    /** booking-events siempre; recommendation-events también para {@code RecommendationGenerated}. */
    List<ProducerRecord<String, BusinessEvent<?>>> toRecords(BusinessEvent<?> event) {
        List<ProducerRecord<String, BusinessEvent<?>>> records = new ArrayList<>();
        records.add(toRecord(topic, event));
        if (RECOMMENDATION_GENERATED.equals(event.eventType()) && recommendationTopic != null
                && !recommendationTopic.isBlank() && !recommendationTopic.equals(topic)) {
            records.add(toRecord(recommendationTopic, event));
        }
        return records;
    }

    ProducerRecord<String, BusinessEvent<?>> toRecord(String targetTopic, BusinessEvent<?> event) {
        ProducerRecord<String, BusinessEvent<?>> record = new ProducerRecord<>(targetTopic, event.aggregateId(), event);
        header(record, "__TypeId__", typeIdHeader);
        header(record, "X-Correlation-Id", event.correlationId());
        header(record, "eventId", String.valueOf(event.eventId()));
        header(record, "eventType", event.eventType());
        header(record, "schemaVersion", String.valueOf(event.version()));
        return record;
    }

    private static void header(ProducerRecord<?, ?> record, String name, String value) {
        if (value != null) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
