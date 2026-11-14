package com.riwi.skillbridge.ai.application.port.in;

import com.riwi.skillbridge.ai.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationResult;

public interface GenerateRecommendationUseCase {
    RecommendationResult recommend(RecommendationRequest request);
}
