package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.RecommendationInputType;

import java.util.UUID;

public interface GenerateRecommendationUseCase {

    /**
     * Generates an AI recommendation for the given goal.
     *
     * @param goal      the user's stated objective
     * @param userId    the authenticated user's ID (for event tracing)
     * @param userEmail the authenticated user's email (for event actor fields)
     * @param inputType the type of input used (TEXT, VOICE, IMAGE)
     * @return the recommendation text returned by the AI model
     */
    String recommend(String goal, UUID userId, String userEmail, RecommendationInputType inputType);
}
