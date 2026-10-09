package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.recommendation.InputType;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AiRecommendationRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GenerateRecommendationUseCase useCase;
    public AiController(GenerateRecommendationUseCase useCase) { this.useCase = useCase; }

    @PostMapping("/recommendations")
    public RecommendationResult recommend(@Valid @RequestBody AiRecommendationRequest request, Authentication authentication) {
        return useCase.recommend(new RecommendationRequest(InputType.TEXT, request.goal(), null, UUID.nameUUIDFromBytes(authentication.getName().getBytes())));
    }
}
