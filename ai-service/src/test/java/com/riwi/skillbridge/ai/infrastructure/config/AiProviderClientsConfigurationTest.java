package com.riwi.skillbridge.ai.infrastructure.config;

import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.GeminiAiAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.GeminiSpeechToTextAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.GeminiVisionAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.OpenAiAiAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Un proveedor sin clave no crea bean (y por tanto queda fuera de la cadena); con clave sí. Sin llamadas de red. */
class AiProviderClientsConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AiProviderProperties.class)
            .withBean(AiErrorClassifier.class)
            .withUserConfiguration(AiProviderClientsConfiguration.class);

    @Test
    void noKeysNoProviderBeans() {
        runner.withPropertyValues("spring.ai.google.genai.api-key=", "spring.ai.openai.api-key=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(GeminiAiAdapter.class);
                    assertThat(ctx).doesNotHaveBean(GeminiVisionAdapter.class);
                    assertThat(ctx).doesNotHaveBean(GeminiSpeechToTextAdapter.class);
                    assertThat(ctx).doesNotHaveBean(OpenAiAiAdapter.class);
                });
    }

    @Test
    void geminiKeyCreatesOnlyGeminiAdapters() {
        runner.withPropertyValues("spring.ai.google.genai.api-key=test-not-a-real-key", "spring.ai.openai.api-key=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(GeminiAiAdapter.class);
                    assertThat(ctx).hasSingleBean(GeminiVisionAdapter.class);
                    assertThat(ctx).hasSingleBean(GeminiSpeechToTextAdapter.class);
                    assertThat(ctx).doesNotHaveBean(OpenAiAiAdapter.class);
                });
    }

    @Test
    void openAiKeyCreatesOpenAiAdapter() {
        runner.withPropertyValues("spring.ai.google.genai.api-key=", "spring.ai.openai.api-key=test-not-a-real-key")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OpenAiAiAdapter.class);
                    assertThat(ctx).doesNotHaveBean(GeminiAiAdapter.class);
                });
    }
}
