package com.riwi.skillbridge.payment;

import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.application.port.out.WebhookVerifierPort;
import com.riwi.skillbridge.payment.support.TestInfrastructure;
import com.riwi.skillbridge.payment.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.riwi.skillbridge.payment.support.StripeWebhookFixtures.paymentIntentEvent;
import static com.riwi.skillbridge.payment.support.StripeWebhookFixtures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Arranca sin STRIPE_SECRET_KEY ni STRIPE_WEBHOOK_SECRET; los endpoints de pago responden 503, nunca un éxito simulado. */
@SpringBootTest
@AutoConfigureMockMvc
class StartupWithoutStripeKeysTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry, false);
    }

    @Autowired MockMvc mvc;
    @Autowired PaymentGatewayPort gateway;
    @Autowired WebhookVerifierPort verifier;
    @Autowired JdbcTemplate jdbc;

    @Test
    void contextStartsAndReportsGatewayNotConfigured() throws Exception {
        assertThat(gateway.isConfigured()).isFalse();
        assertThat(verifier.isConfigured()).isFalse();
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void authorizeIs503ProblemDetail() throws Exception {
        UUID bookingId = UUID.randomUUID();
        mvc.perform(post("/api/payments/authorize")
                        .header("Authorization", "Bearer " + TestJwt.customer())
                        .header("Idempotency-Key", "no-keys-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\",\"amount\":50.00}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_result WHERE booking_id = ?", Integer.class, bookingId))
                .isZero();
    }

    @Test
    void webhookIs503WhenSecretIsMissing() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String payload = paymentIntentEvent("evt_nokeys_" + bookingId, "payment_intent.succeeded", "pi_n", bookingId, null);
        mvc.perform(post("/api/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("Stripe-Signature", sign(payload, "whsec_anything")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_stripe_event WHERE booking_id = ?", Integer.class,
                bookingId)).isZero();
    }
}
