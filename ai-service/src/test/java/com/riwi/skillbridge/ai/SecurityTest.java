package com.riwi.skillbridge.ai;

import com.riwi.skillbridge.ai.support.AbstractIntegrationTest;
import com.riwi.skillbridge.ai.support.TestJwt;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Misma política que el monolito: 401 sin token, CSRF en POST con cookie, Bearer exento, admin solo ROLE_ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest extends AbstractIntegrationTest {

    private static final String BODY = "{\"goal\":\"Quiero aprender Spring Boot\"}";

    @Autowired MockMvc mvc;

    @Test
    void postWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/admin/ai/providers")).andExpect(status().isUnauthorized());
    }

    @Test
    void cookiePostWithoutCsrfIs403() throws Exception {
        mvc.perform(post("/api/ai/recommendations")
                        .cookie(new Cookie("access_token", TestJwt.customer()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void cookiePostWithCsrfPassesSecurity() throws Exception {
        // Pasa la seguridad; sin proveedores configurados la respuesta es el 503 de la cadena vacía.
        mvc.perform(post("/api/ai/recommendations")
                        .cookie(new Cookie("access_token", TestJwt.customer()))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void bearerPostIsExemptFromCsrf() throws Exception {
        mvc.perform(post("/api/ai/recommendations")
                        .header("Authorization", "Bearer " + TestJwt.customer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void validationErrorIs400() throws Exception {
        mvc.perform(post("/api/ai/recommendations")
                        .header("Authorization", "Bearer " + TestJwt.customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        String expired = TestJwt.builder(UUID.randomUUID(), "x@test.local", "CUSTOMER", TestJwt.KEY_ID, "skillbridge",
                Instant.now().minus(1, ChronoUnit.MINUTES));
        mvc.perform(post("/api/ai/recommendations").header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownKidIs401() throws Exception {
        String token = TestJwt.builder(UUID.randomUUID(), "x@test.local", "CUSTOMER", "other-key", "skillbridge",
                Instant.now().plus(5, ChronoUnit.MINUTES));
        mvc.perform(post("/api/ai/recommendations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyHs256TokenIs401() throws Exception {
        mvc.perform(post("/api/ai/recommendations").header("Authorization", "Bearer " + TestJwt.legacyHs256())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointForbiddenForCustomer() throws Exception {
        mvc.perform(get("/api/admin/ai/providers").header("Authorization", "Bearer " + TestJwt.customer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowedForAdmin() throws Exception {
        mvc.perform(get("/api/admin/ai/providers").header("Authorization", "Bearer " + TestJwt.admin()))
                .andExpect(status().isOk());
    }

    @Test
    void correlationIdIsEchoed() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Correlation-Id", "abc-123"));
    }
}
