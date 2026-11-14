package com.riwi.skillbridge.ai.infrastructure.config;

import com.google.genai.Client;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.GeminiAiAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.GeminiSpeechToTextAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.GeminiVisionAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.OpenAiAiAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clientes de proveedores de IA construidos <strong>solo si su clave existe</strong>.
 *
 * <p>Diferencia deliberada con el monolito: allí {@code GoogleGenAiChatAutoConfiguration} impide arrancar sin
 * {@code GEMINI_API_KEY} (hallazgo W0). ai-service no usa la autoconfiguración de Spring AI (solo dependencias de
 * modelo, sin {@code spring-ai-autoconfigure-*}); un proveedor sin clave no tiene bean y
 * {@link AiFallbackConfiguration} lo deja fuera de la cadena. Si la cadena queda vacía, los adaptadores compuestos
 * lanzan {@code AiProviderException(UNAVAILABLE)} y la API responde 503 ProblemDetail (ADR-0007 §6): nunca una
 * respuesta simulada.
 *
 * <p>Cada proveedor tiene su propio {@link ChatClient}; en el monolito ambos adaptadores compartían el único
 * {@code ChatClient.Builder} autoconfigurado (Gemini).
 */
@Configuration
public class AiProviderClientsConfiguration {

    static final String GEMINI_KEY = "${spring.ai.google.genai.api-key:${GEMINI_API_KEY:}}";
    static final String OPENAI_KEY = "${spring.ai.openai.api-key:${OPENAI_API_KEY:}}";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiProviderClientsConfiguration.class);

    public AiProviderClientsConfiguration(org.springframework.core.env.Environment env) {
        // Aviso operativo sin imprimir nunca la clave (ADR-0007 §8).
        if (env.resolvePlaceholders(GEMINI_KEY).isBlank()) {
            log.warn("AI provider [gemini] disabled: GEMINI_API_KEY is not configured (chat, vision and STT candidates omitted)");
        }
        if (env.resolvePlaceholders(OPENAI_KEY).isBlank()) {
            log.warn("AI provider [openai] disabled: OPENAI_API_KEY is not configured (chat candidate omitted)");
        }
    }

    @Configuration
    @ConditionalOnExpression("!'" + GEMINI_KEY + "'.isBlank()")
    static class Gemini {

        @Bean(destroyMethod = "destroy")
        GoogleGenAiChatModel geminiChatModel(
                @org.springframework.beans.factory.annotation.Value(GEMINI_KEY) String apiKey,
                @org.springframework.beans.factory.annotation.Value("${spring.ai.google.genai.chat.options.model:gemini-3.8-flash}") String model,
                @org.springframework.beans.factory.annotation.Value("${spring.ai.google.genai.chat.options.temperature:0.2}") double temperature,
                @org.springframework.beans.factory.annotation.Value("${spring.ai.google.genai.chat.options.max-output-tokens:2048}") int maxOutputTokens) {
            return GoogleGenAiChatModel.builder()
                    .genAiClient(Client.builder().apiKey(apiKey).build())
                    .defaultOptions(GoogleGenAiChatOptions.builder()
                            .model(model)
                            .temperature(temperature)
                            .maxOutputTokens(maxOutputTokens)
                            .build())
                    .build();
        }

        @Bean
        GeminiAiAdapter geminiAiAdapter(GoogleGenAiChatModel model, AiProviderProperties properties, AiErrorClassifier classifier) {
            return new GeminiAiAdapter(ChatClient.builder(model), properties, classifier);
        }

        @Bean
        GeminiVisionAdapter geminiVisionAdapter(GoogleGenAiChatModel model) {
            return new GeminiVisionAdapter(ChatClient.builder(model));
        }

        @Bean
        GeminiSpeechToTextAdapter geminiSpeechToTextAdapter(GoogleGenAiChatModel model) {
            return new GeminiSpeechToTextAdapter(ChatClient.builder(model));
        }
    }

    @Configuration
    @ConditionalOnExpression("!'" + OPENAI_KEY + "'.isBlank()")
    static class OpenAi {

        @Bean
        OpenAiChatModel openAiChatModel(
                @org.springframework.beans.factory.annotation.Value(OPENAI_KEY) String apiKey,
                @org.springframework.beans.factory.annotation.Value("${OPENAI_CHAT_MODEL:gpt-4o-mini}") String model) {
            return OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder().apiKey(apiKey).build())
                    .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                    .build();
        }

        @Bean
        OpenAiAiAdapter openAiAiAdapter(OpenAiChatModel model, AiProviderProperties properties, AiErrorClassifier classifier) {
            return new OpenAiAiAdapter(ChatClient.builder(model), properties, classifier);
        }
    }
}
