package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.in.GetTrendingServicesUseCase;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.RecommendationInputType;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AiRecommendationRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.TrendingServiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GenerateRecommendationUseCase recommendationUseCase;
    private final GetTrendingServicesUseCase trendingUseCase;
    private final UserRepositoryPort userRepository;

    public AiController(GenerateRecommendationUseCase recommendationUseCase,
                        GetTrendingServicesUseCase trendingUseCase,
                        UserRepositoryPort userRepository) {
        this.recommendationUseCase = recommendationUseCase;
        this.trendingUseCase = trendingUseCase;
        this.userRepository = userRepository;
    }

    @PostMapping("/recommendations")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> recommend(@Valid @RequestBody AiRecommendationRequest request,
                                         Authentication authentication) {
        UserAccount user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        String result = recommendationUseCase.recommend(
                request.goal(),
                user.id(),
                user.email(),
                RecommendationInputType.TEXT
        );
        return Map.of("recommendation", result);
    }

    @GetMapping("/trending")
    @ResponseStatus(HttpStatus.OK)
    public List<TrendingServiceResponse> trending() {
        return trendingUseCase.getTrending();
    }
}
