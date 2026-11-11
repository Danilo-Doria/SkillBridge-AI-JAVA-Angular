package com.riwi.skillbridge.application.port.out.event;

import com.riwi.skillbridge.domain.model.RecommendationInputType;

import java.util.UUID;

/**
 * Payload for the RecommendationGenerated event (v2).
 * Published after the AI model successfully returns a recommendation.
 * Includes latency and the AI provider and model used for analytics and traceability.
 */
public record RecommendationGeneratedPayload(
        UUID recommendationId,
        UUID userId,
        String goal,
        RecommendationInputType inputType,
        String modelUsed,
        long latencyMs,
        String provider
) {
    /**
     * Backward-compatible 6-argument constructor for existing consumers.
     */
    public RecommendationGeneratedPayload(
            UUID recommendationId,
            UUID userId,
            String goal,
            RecommendationInputType inputType,
            String modelUsed,
            long latencyMs
    ) {
        this(recommendationId, userId, goal, inputType, modelUsed, latencyMs, null);
    }
}
