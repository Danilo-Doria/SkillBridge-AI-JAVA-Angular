package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.infrastructure.adapter.out.ai.ElevenLabsTextToSpeechAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GeminiAiAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GeminiSpeechToTextAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GeminiVisionAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GroqSpeechToTextAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.NoOpTextToSpeechAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.OpenAiAiAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate.AiModelCandidate;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackAiRecommendationAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackSpeechToTextAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackTextToSpeechAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackVisionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring configuration for AI fallback chains across all capabilities.
 * Resolves active candidates safely, excluding unconfigured providers without leaking secrets.
 */
@Configuration
public class AiFallbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiFallbackConfiguration.class);

    @Value("${spring.ai.google.genai.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${spring.ai.openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${app.ai.groq.api-key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    @Value("${elevenlabs.api-key:${ELEVENLABS_API_KEY:}}")
    private String elevenLabsApiKey;

    @Bean
    public List<FallbackAiRecommendationAdapter.CandidateExecutor> recommendationCandidates(
            ObjectProvider<GeminiAiAdapter> geminiAdapterProvider,
            ObjectProvider<OpenAiAiAdapter> openAiAdapterProvider,
            AiProviderProperties properties
    ) {
        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = new ArrayList<>();
        int timeoutSeconds = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10;

        GeminiAiAdapter gemini = geminiAdapterProvider.getIfAvailable();
        if (gemini != null) {
            boolean hasKey = geminiApiKey != null && !geminiApiKey.isBlank();
            if (hasKey) {
                candidates.add(new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, timeoutSeconds),
                        gemini
                ));
            } else {
                log.warn("AI recommendation candidate [gemini:gemini-3.8-flash] omitted at startup: API key is not configured");
            }
        }

        OpenAiAiAdapter openAi = openAiAdapterProvider.getIfAvailable();
        if (openAi != null) {
            boolean hasKey = openAiApiKey != null && !openAiApiKey.isBlank();
            if (hasKey) {
                candidates.add(new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, timeoutSeconds),
                        openAi
                ));
            } else {
                log.warn("AI recommendation candidate [openai:gpt-4o-mini] omitted at startup: API key is not configured");
            }
        }

        return candidates;
    }

    @Bean
    public List<FallbackSpeechToTextAdapter.CandidateExecutor> speechToTextCandidates(
            ObjectProvider<GroqSpeechToTextAdapter> groqProvider,
            ObjectProvider<GeminiSpeechToTextAdapter> geminiProvider,
            AiProviderProperties properties
    ) {
        List<FallbackSpeechToTextAdapter.CandidateExecutor> candidates = new ArrayList<>();
        int timeoutSeconds = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10;

        GroqSpeechToTextAdapter groq = groqProvider.getIfAvailable();
        if (groq != null) {
            boolean hasKey = groqApiKey != null && !groqApiKey.isBlank();
            if (hasKey) {
                candidates.add(new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("groq", "whisper-large-v3-turbo", 1, timeoutSeconds),
                        groq
                ));
            } else {
                log.warn("STT candidate [groq:whisper-large-v3-turbo] omitted at startup: API key is not configured");
            }
        }

        GeminiSpeechToTextAdapter gemini = geminiProvider.getIfAvailable();
        if (gemini != null) {
            boolean hasKey = geminiApiKey != null && !geminiApiKey.isBlank();
            if (hasKey) {
                candidates.add(new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-audio", 2, timeoutSeconds),
                        gemini
                ));
            } else {
                log.warn("STT candidate [gemini:gemini-audio] omitted at startup: API key is not configured");
            }
        }

        return candidates;
    }

    @Bean
    public List<FallbackVisionAdapter.CandidateExecutor> visionCandidates(
            ObjectProvider<GeminiVisionAdapter> geminiVisionProvider,
            AiProviderProperties properties
    ) {
        List<FallbackVisionAdapter.CandidateExecutor> candidates = new ArrayList<>();
        int timeoutSeconds = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10;

        GeminiVisionAdapter gemini = geminiVisionProvider.getIfAvailable();
        if (gemini != null) {
            boolean hasKey = geminiApiKey != null && !geminiApiKey.isBlank();
            if (hasKey) {
                candidates.add(new FallbackVisionAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, timeoutSeconds),
                        gemini
                ));
            } else {
                log.warn("Vision candidate [gemini:gemini-3.8-flash] omitted at startup: API key is not configured");
            }
        }

        return candidates;
    }

    @Bean
    public List<FallbackTextToSpeechAdapter.CandidateExecutor> textToSpeechCandidates(
            ObjectProvider<ElevenLabsTextToSpeechAdapter> elevenLabsProvider,
            ObjectProvider<NoOpTextToSpeechAdapter> noOpProvider,
            AiProviderProperties properties
    ) {
        List<FallbackTextToSpeechAdapter.CandidateExecutor> candidates = new ArrayList<>();
        int timeoutSeconds = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10;

        ElevenLabsTextToSpeechAdapter elevenLabs = elevenLabsProvider.getIfAvailable();
        if (elevenLabs != null) {
            boolean hasKey = elevenLabsApiKey != null && !elevenLabsApiKey.isBlank();
            if (hasKey) {
                candidates.add(new FallbackTextToSpeechAdapter.CandidateExecutor(
                        AiModelCandidate.of("elevenlabs", "multilingual-v2", 1, timeoutSeconds),
                        elevenLabs
                ));
            } else {
                log.warn("TTS candidate [elevenlabs:multilingual-v2] omitted at startup: API key is not configured");
            }
        }

        NoOpTextToSpeechAdapter noOp = noOpProvider.getIfAvailable();
        if (noOp != null) {
            candidates.add(new FallbackTextToSpeechAdapter.CandidateExecutor(
                    AiModelCandidate.of("noop", "noop", 99, timeoutSeconds),
                    noOp
            ));
        }

        return candidates;
    }
}
