package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.recommendation.Recommendation;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class AiRecommendationService implements GenerateRecommendationUseCase {
    private final AiRecommendationPort ai;
    private final OfferingRepositoryPort offerings;
    public AiRecommendationService(AiRecommendationPort ai, OfferingRepositoryPort offerings) { this.ai = ai; this.offerings = offerings; }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        List<Offering> active = offerings.findAllActive();
        String explanation = ai.recommend(request, active);
        List<Recommendation> selected = IntStream.range(0, Math.min(3, active.size()))
            .mapToObj(index -> new Recommendation(active.get(index).id(), 1.0 - (index * 0.1), "Servicio disponible relacionado con tu necesidad"))
            .toList();
        return new RecommendationResult(UUID.randomUUID(), explanation, request.inputType(), selected);
    }
}
