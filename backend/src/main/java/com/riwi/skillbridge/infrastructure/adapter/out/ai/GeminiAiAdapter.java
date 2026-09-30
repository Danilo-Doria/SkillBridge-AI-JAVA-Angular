package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiAiAdapter implements AiRecommendationPort {
        private final ChatClient chatClient;

        public GeminiAiAdapter(ChatClient.Builder chatClientBuilder) {
                this.chatClient = chatClientBuilder.build();
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
                throw new BusinessRuleException("Gemini no devolvió una respuesta válida");
            }
            return response;
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("Gemini no está disponible; inténtalo de nuevo");
        }
    }
}
