package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.RecommendationEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.RecommendationGeneratedPayload;
import com.riwi.skillbridge.application.port.out.event.RecommendationRequestedPayload;
import com.riwi.skillbridge.domain.model.RecommendationInputType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service implementing GenerateRecommendationUseCase.
 *
 * Responsibilities:
 *   1. Assign a unique recommendationId to each request.
 *   2. Publish a RecommendationRequested event before calling the AI model.
 *   3. Call the AI model and measure latency.
 *   4. Publish a RecommendationGenerated event with the result and latency.
 *
 * The events are consumed by KafkaAnalyticsConsumer in a separate consumer group,
 * keeping analytics completely decoupled from the recommendation use case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationService implements GenerateRecommendationUseCase {

    private final AiRecommendationPort ai;
    private final OfferingRepositoryPort offerings;
    private final RecommendationEventPublisherPort eventPublisher;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-3.8-flash}")
    private String modelUsed;

    @Override
    public String recommend(String goal, UUID userId, String userEmail, RecommendationInputType inputType) {
        UUID recommendationId = UUID.randomUUID();
        String correlationId = CorrelationIdHolder.get() != null
                ? CorrelationIdHolder.get()
                : UUID.randomUUID().toString();

        // Step 1: publish RecommendationRequested event
        publishRequestedEvent(recommendationId, userId, userEmail, goal, inputType, correlationId);

        // Step 2: call AI model and measure latency
        long start = System.currentTimeMillis();
        String result = ai.recommend(goal, offerings.findAllActive());
        long latencyMs = System.currentTimeMillis() - start;

        log.info("AI recommendation generated: recommendationId={}, latencyMs={}, model={}",
                recommendationId, latencyMs, modelUsed);

        // Step 3: publish RecommendationGenerated event
        publishGeneratedEvent(recommendationId, userId, userEmail, goal, inputType, latencyMs, correlationId);

        return result;
    }

    private void publishRequestedEvent(UUID recommendationId, UUID userId, String userEmail,
                                       String goal, RecommendationInputType inputType, String correlationId) {
        RecommendationRequestedPayload payload =
                new RecommendationRequestedPayload(recommendationId, userId, goal, inputType);

        BusinessEvent<RecommendationRequestedPayload> event = new BusinessEvent<>(
                UUID.randomUUID(),
                "RecommendationRequested",
                recommendationId.toString(),
                "Recommendation",
                Instant.now(),
                correlationId,
                1,
                payload,
                userId.toString(),
                userEmail,
                "CUSTOMER",
                "RECOMMEND",
                "RECOMMENDATION",
                recommendationId.toString()
        );
        eventPublisher.publish(event);
    }

    private void publishGeneratedEvent(UUID recommendationId, UUID userId, String userEmail,
                                       String goal, RecommendationInputType inputType,
                                       long latencyMs, String correlationId) {
        RecommendationGeneratedPayload payload =
                new RecommendationGeneratedPayload(recommendationId, userId, goal, inputType, modelUsed, latencyMs);

        BusinessEvent<RecommendationGeneratedPayload> event = new BusinessEvent<>(
                UUID.randomUUID(),
                "RecommendationGenerated",
                recommendationId.toString(),
                "Recommendation",
                Instant.now(),
                correlationId,
                1,
                payload,
                userId.toString(),
                userEmail,
                "CUSTOMER",
                "RECOMMEND",
                "RECOMMENDATION",
                recommendationId.toString()
        );
        eventPublisher.publish(event);
    }
}
