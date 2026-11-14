package com.riwi.skillbridge.payment;

import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.support.TestInfrastructure;
import com.riwi.skillbridge.payment.support.TestJwt;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Misma política que el monolito: 401 sin token, CSRF en POST con cookie, Bearer exento, webhook público sin CSRF. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    private static final String BODY = "{\"bookingId\":\"" + UUID.randomUUID() + "\",\"amount\":50.00}";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry, true);
    }

    @Autowired MockMvc mvc;
    @MockitoBean PaymentGatewayPort gateway;

    @Test
    void authorizeWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test
    void cookiePostWithoutCsrfIs403() throws Exception {
        mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", "k2")
                        .cookie(new Cookie("access_token", TestJwt.customer()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test
    void cookiePostWithCsrfPassesSecurity() throws Exception {
        when(gateway.isConfigured()).thenReturn(true);
        // Pasa la seguridad; la reserva no existe en el read model → 409 controlado.
        mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", "k3")
                        .cookie(new Cookie("access_token", TestJwt.customer()))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void bearerPostIsExemptFromCsrf() throws Exception {
        when(gateway.isConfigured()).thenReturn(true);
        mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", "k4")
                        .header("Authorization", "Bearer " + TestJwt.customer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredAndLegacyTokensAre401() throws Exception {
        String expired = TestJwt.builder(UUID.randomUUID(), "x@test.local", "CUSTOMER", TestJwt.KEY_ID, "skillbridge",
                Instant.now().minus(1, ChronoUnit.MINUTES));
        mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", "k5")
                        .header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", "k6")
                        .header("Authorization", "Bearer " + TestJwt.legacyHs256())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookIsPublicAndCsrfExemptButRequiresSignature() throws Exception {
        mvc.perform(post("/api/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing Stripe-Signature header"));
    }

    @Test
    void healthIsPublicAndCorrelationIdIsEchoed() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
    }
}
