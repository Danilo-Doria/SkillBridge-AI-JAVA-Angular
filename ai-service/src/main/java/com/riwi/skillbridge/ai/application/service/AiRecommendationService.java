package com.riwi.skillbridge.ai.application.service;

import com.riwi.skillbridge.ai.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.ai.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.ai.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.ai.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.ai.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.ai.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.ai.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.ai.application.port.out.event.RecommendationGeneratedPayload;
import com.riwi.skillbridge.ai.application.recommendation.Recommendation;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.ai.domain.exception.AiProviderException;
import com.riwi.skillbridge.ai.domain.model.Offering;
import com.riwi.skillbridge.ai.domain.model.RecommendationInputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiRecommendationService implements GenerateRecommendationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationService.class);

    private final AiRecommendationPort ai;
    private final OfferingRepositoryPort offerings;
    private final AuditEventPublisherPort events;

    /**
     * ai-service no tiene preselección RAG (la búsqueda semántica/pgvector permanece en el monolito, decisión Q4):
     * el LLM recibe todas las ofertas activas del read model {@code ai.offering_view}, igual que el constructor de
     * tres argumentos del monolito.
     */
    public AiRecommendationService(AiRecommendationPort ai, OfferingRepositoryPort offerings, AuditEventPublisherPort events) {
        this.ai = ai;
        this.offerings = offerings;
        this.events = events;
    }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        UUID id = UUID.randomUUID();
        String correlation = CorrelationIdHolder.get() == null ? UUID.randomUUID().toString() : CorrelationIdHolder.get();

        events.publish(new BusinessEvent<>(
                UUID.randomUUID(),
                "RecommendationRequested",
                id.toString(),
                "Recommendation",
                Instant.now(),
                correlation,
                1,
                request,
                String.valueOf(request.userId()),
                null,
                null,
                "REQUEST",
                "RECOMMENDATION",
                id.toString()
        ));

        List<Offering> active = offerings.findAllActive();

        long startNanos = System.currentTimeMillis();
        AiStructuredResponse aiResponse = ai.recommend(request, active);
        long latencyMs = System.currentTimeMillis() - startNanos;

        String provider = aiResponse.resolvedByProvider() != null ? aiResponse.resolvedByProvider() : "unknown";
        String model = aiResponse.resolvedByModel() != null ? aiResponse.resolvedByModel() : "unknown";

        log.info("AI recommendation completed recommendationId={} provider={} model={} latencyMs={} correlationId={}",
                id, provider, model, latencyMs, correlation);

        Map<UUID, String> titles = active.stream()
                .collect(Collectors.toMap(Offering::id, Offering::title, (a, b) -> a));

        List<Recommendation> selected = aiResponse.recommendations().stream()
                .map(item -> {
                    try {
                        UUID offeringId = UUID.fromString(item.offeringId());
                        String title = titles.get(offeringId);
                        if (title == null) {
                            return null; // La IA citó una oferta que no existe o no está activa: se descarta
                        }
                        return new Recommendation(offeringId, title, item.score(), item.reason());
                    } catch (IllegalArgumentException e) {
                        return null; // Ignore if the AI hallucinated an invalid UUID
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        RecommendationResult result = new RecommendationResult(id, aiResponse.explanation(), request.inputType(), request.normalizedNeed(), selected);

        RecommendationInputType domainInputType = request.inputType() != null
                ? RecommendationInputType.valueOf(request.inputType().name())
                : RecommendationInputType.TEXT;

        RecommendationGeneratedPayload payload = new RecommendationGeneratedPayload(
                id,
                request.userId(),
                request.normalizedNeed(),
                domainInputType,
                model,
                latencyMs,
                provider,
                selected.stream().map(Recommendation::offeringId).distinct().toList()
        );

        events.publish(new BusinessEvent<>(
                UUID.randomUUID(),
                "RecommendationGenerated",
                id.toString(),
                "Recommendation",
                Instant.now(),
                correlation,
                2,
                payload,
                String.valueOf(request.userId()),
                null,
                null,
                "GENERATE",
                "RECOMMENDATION",
                id.toString()
        ));

        return result;
    }
}
