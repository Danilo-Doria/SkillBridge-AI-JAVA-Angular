package com.riwi.skillbridge.ai.application.port.out;

import com.riwi.skillbridge.ai.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.ai.domain.model.Offering;
import java.util.List;

public interface AiRecommendationPort {
    AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings);
}