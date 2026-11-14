package com.riwi.skillbridge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.ai.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.ai.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.ai.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.ai.application.recommendation.InputType;
import com.riwi.skillbridge.ai.application.recommendation.Recommendation;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.ai.domain.model.MentorshipMode;
import com.riwi.skillbridge.ai.domain.model.Offering;
import com.riwi.skillbridge.ai.support.AbstractIntegrationTest;
import com.riwi.skillbridge.ai.support.JsonShape;
import com.riwi.skillbridge.ai.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de forma JSON frente a los records del monolito (fixtures en {@code src/test/resources/contracts/},
 * derivados de feature/104 @ c02be4d). El proveedor de IA se sustituye por un doble de prueba (solo en src/test).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OfferingRepositoryPort offerings;

    @MockitoBean AiRecommendationPort aiPort;

    @Test
    void recommendationResponseMatchesMonolithContract() throws Exception {
        UUID offeringId = UUID.randomUUID();
        offerings.upsert(new Offering(offeringId, UUID.randomUUID(), "Mentoría Spring Boot", "APIs con Spring",
                "Backend", new BigDecimal("50.00"), true, MentorshipMode.LIVE, 2), UUID.randomUUID(), Instant.now());
        UUID unknown = UUID.randomUUID();
        when(aiPort.recommend(any(), anyList())).thenReturn(new AiStructuredResponse(
                "Te recomiendo esta mentoría.",
                List.of(new AiStructuredResponse.AiItem(offeringId.toString(), 0.92, "Cubre el objetivo."),
                        new AiStructuredResponse.AiItem(unknown.toString(), 0.5, "Oferta inexistente")),
                "gemini", "gemini-3.8-flash"));

        String json = mvc.perform(post("/api/ai/recommendations")
                        .header("Authorization", "Bearer " + TestJwt.customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Quiero aprender Spring Boot\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(json);
        assertThat(JsonShape.differences(JsonShape.fixture("/contracts/recommendation-result.json"), body)).isEmpty();
        assertThat(body.get("recommendations")).hasSize(1); // la oferta que no existe en el read model se descarta
        assertThat(body.at("/recommendations/0/offeringTitle").asText()).isEqualTo("Mentoría Spring Boot");
        assertThat(body.get("inputType").asText()).isEqualTo("TEXT");
    }

    @Test
    void recommendationResultSerializationMatchesContract() throws Exception {
        RecommendationResult result = new RecommendationResult(UUID.randomUUID(), "x", InputType.VOICE, "texto",
                List.of(new Recommendation(UUID.randomUUID(), "Título", 0.7, "motivo")));
        JsonNode body = objectMapper.valueToTree(result);
        assertThat(JsonShape.differences(JsonShape.fixture("/contracts/recommendation-result.json"), body)).isEmpty();
    }

    @Test
    void adminProvidersResponseMatchesMonolithContract() throws Exception {
        String json = mvc.perform(get("/api/admin/ai/providers")
                        .header("Authorization", "Bearer " + TestJwt.admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(json);
        assertThat(JsonShape.differences(JsonShape.fixture("/contracts/admin-ai-providers.json"), body)).isEmpty();
        assertThat(body.get("capabilities").fieldNames()).toIterable()
                .containsExactly("chat", "vision", "stt", "tts", "rag", "embedding");
    }
}
