package com.riwi.skillbridge.ai.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRecommendationRequest(@NotBlank @Size(max = 1000) String goal) {}
