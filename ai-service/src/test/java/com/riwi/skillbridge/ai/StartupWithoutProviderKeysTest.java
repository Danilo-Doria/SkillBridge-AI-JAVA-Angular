package com.riwi.skillbridge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.ai.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.ai.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.ai.application.port.out.TextToSpeechPort;
import com.riwi.skillbridge.ai.application.port.out.VisionPort;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.fallback.FallbackAiRecommendationAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.fallback.FallbackSpeechToTextAdapter;
import com.riwi.skillbridge.ai.infrastructure.adapter.out.ai.fallback.FallbackVisionAdapter;
import com.riwi.skillbridge.ai.support.AbstractIntegrationTest;
import com.riwi.skillbridge.ai.support.JsonShape;
import com.riwi.skillbridge.ai.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ai-service arranca sin ninguna clave de proveedor (a diferencia del monolito, hallazgo W0), sin RabbitMQ y sin la
 * BD del monolito; sin proveedores en la cadena, cada capacidad responde 503 ProblemDetail (ADR-0007 §6), nunca una
 * respuesta inventada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StartupWithoutProviderKeysTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AiRecommendationPort recommendationPort;
    @Autowired VisionPort visionPort;
    @Autowired SpeechToTextPort speechPort;
    @Autowired TextToSpeechPort ttsPort;
    @Autowired List<FallbackAiRecommendationAdapter.CandidateExecutor> recommendationCandidates;

    @Test
    void startsWithEmptyFallbackChainsAndCompositeAdaptersInjected() {
        assertThat(recommendationCandidates).isEmpty();
        assertThat(recommendationPort).isInstanceOf(FallbackAiRecommendationAdapter.class);
        assertThat(visionPort).isInstanceOf(FallbackVisionAdapter.class);
        assertThat(speechPort).isInstanceOf(FallbackSpeechToTextAdapter.class);
        assertThat(((FallbackAiRecommendationAdapter) recommendationPort).getCandidates()).isEmpty();
        assertThat(ttsPort).isNotNull();
    }

    @Test
    void healthIsUpWithoutProviderKeys() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void recommendationReturns503ProblemDetailWithMonolithShape() throws Exception {
        MvcResult result = mvc.perform(post("/api/ai/recommendations")
                        .header("Authorization", "Bearer " + TestJwt.customer())
                        .header("X-Correlation-Id", "c0ffee00-0000-4000-8000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Quiero aprender Spring Boot\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("Unavailable"))
                .andExpect(jsonPath("$.correlationId").value("c0ffee00-0000-4000-8000-000000000001"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(JsonShape.differences(JsonShape.fixture("/contracts/ai-unavailable-problem.json"), body)).isEmpty();
    }

    @Test
    void ttsReturns503WhenNoSynthesisProviderIsAvailable() throws Exception {
        mvc.perform(post("/api/ai/tts")
                        .header("Authorization", "Bearer " + TestJwt.customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hola\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("Unavailable"));
    }

    @Test
    void imageRecommendationReturns503WhenNoVisionProviderIsAvailable() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/ai/recommendations/image").file(file)
                        .header("Authorization", "Bearer " + TestJwt.customer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("Unavailable"));
    }

    @Test
    void voiceRecommendationReturns503WhenNoSpeechProviderIsAvailable() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "nota.webm", "audio/webm", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/ai/recommendations/voice").file(file)
                        .header("Authorization", "Bearer " + TestJwt.customer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("Unavailable"));
    }

    @Test
    void knowledgeAndTrendingAreNotServedHere() throws Exception {
        mvc.perform(get("/api/ai/trending").header("Authorization", "Bearer " + TestJwt.customer()))
                .andExpect(status().isNotFound());
    }
}
