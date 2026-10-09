package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.config.AiProviderProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiAiAdapter implements AiRecommendationPort {
    private final ChatClient chatClient;
    private final AiProviderProperties properties;

    public GeminiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public String recommend(String goal, List<Offering> offerings) {
        String catalog = offerings.stream()
                .map(o -> "- %s [%s]: %s".formatted(o.title(), o.category(), o.description()))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Eres el asistente de SkillBridge AI. Recomienda como máximo 3 servicios del catálogo
                que ayuden al usuario a lograr su objetivo. Explica brevemente por qué y propone un
                siguiente paso. No inventes servicios que no estén en el catálogo.

                Objetivo del usuario:
                %s

                Catálogo disponible:
                %s
                """.formatted(goal, catalog);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.isBlank()) {
                throw new AiProviderException(
                    "Gemini did not return a valid response",
                    ErrorType.INTERNAL_ERROR
                );
            }
            return response;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            // Typically thrown when API key is missing or invalid
            throw new AiProviderException(
                "Gemini is not properly configured: " + ex.getMessage(),
                ErrorType.INVALID_INPUT,
                ex
            );
        } catch (RuntimeException ex) {
            // Catches network errors, timeout exceptions, etc.
            throw new AiProviderException(
                "Gemini is temporarily unavailable (timeout: " + properties.getTimeoutSeconds() + "s)",
                ErrorType.UNAVAILABLE,
                ex
            );
        }
    }
}
