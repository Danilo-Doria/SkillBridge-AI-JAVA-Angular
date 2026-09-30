package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class AiRecommendationService implements GenerateRecommendationUseCase {
    private final AiRecommendationPort ai;
    private final OfferingRepositoryPort offerings;

    public AiRecommendationService(AiRecommendationPort ai, OfferingRepositoryPort offerings) {
        this.ai = ai;
        this.offerings = offerings;
    }

    @Override
    public String recommend(String goal) {
        return ai.recommend(goal, offerings.findAllActive());
    }
}
