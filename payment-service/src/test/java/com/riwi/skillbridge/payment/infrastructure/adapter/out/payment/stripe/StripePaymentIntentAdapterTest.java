package com.riwi.skillbridge.payment.infrastructure.adapter.out.payment.stripe;

import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort.GatewayResult;
import com.riwi.skillbridge.payment.domain.exception.PaymentGatewayUnavailableException;
import com.riwi.skillbridge.payment.domain.model.PaymentCommand;
import com.riwi.skillbridge.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Adaptador de Stripe contra un servidor simulado (nunca la API real). */
class StripePaymentIntentAdapterTest {

    private static final String BASE = "https://stripe.test";
    private final PaymentCommand command = new PaymentCommand("idem-1", UUID.randomUUID(), new BigDecimal("49.99"),
            UUID.randomUUID());

    private static StripeProperties props(String secretKey) {
        return new StripeProperties(BASE, secretKey, "whsec_x", "USD", 300L, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private record Fixture(MockRestServiceServer server, StripePaymentIntentAdapter adapter) {}

    private static Fixture fixture(String secretKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(server, new StripePaymentIntentAdapter(builder.build(), props(secretKey)));
    }

    @Test
    void createsPaymentIntentWithOnlyAmountCurrencyAndMetadata() {
        Fixture f = fixture("sk_test_abc");
        f.server().expect(requestTo(BASE + "/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_abc"))
                .andExpect(header("Idempotency-Key", "idem-1"))
                .andExpect(request -> {
                    String body = URLDecoder.decode(((MockClientHttpRequest) request).getBodyAsString(), StandardCharsets.UTF_8);
                    assertThat(body).contains("amount=4999", "currency=usd",
                            "metadata[bookingId]=" + command.bookingId(), "metadata[idempotencyKey]=idem-1",
                            "automatic_payment_methods[enabled]=true");
                    assertThat(body).doesNotContain("payment_method_data", "card[", "number", "cvc", "exp_");
                })
                .andRespond(withSuccess("{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"status\":\"requires_payment_method\","
                        + "\"client_secret\":\"pi_1_secret_2\"}", MediaType.APPLICATION_JSON));

        GatewayResult result = f.adapter().createPaymentIntent(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.paymentIntentId()).isEqualTo("pi_1");
        assertThat(result.clientSecret()).isEqualTo("pi_1_secret_2");
        f.server().verify();
    }

    @Test
    void businessErrorFromStripeIsDeclined() {
        Fixture f = fixture("sk_test_abc");
        f.server().expect(requestTo(BASE + "/v1/payment_intents"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"amount_too_small\",\"message\":\"Amount must be at least $0.50\",\"type\":\"invalid_request_error\"}}"));

        GatewayResult result = f.adapter().createPaymentIntent(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(result.message()).contains("Amount must be at least $0.50");
    }

    @Test
    void outageOrBadCredentialIs503NotDeclinedNorSimulatedSuccess() {
        Fixture f = fixture("sk_test_abc");
        f.server().expect(requestTo(BASE + "/v1/payment_intents")).andRespond(withServerError());
        assertThatThrownBy(() -> f.adapter().createPaymentIntent(command))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        Fixture g = fixture("sk_test_revoked");
        g.server().expect(requestTo(BASE + "/v1/payment_intents")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> g.adapter().createPaymentIntent(command))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    void withoutSecretKeyNoHttpCallIsMade() {
        Fixture f = fixture("");
        assertThat(f.adapter().isConfigured()).isFalse();
        assertThatThrownBy(() -> f.adapter().createPaymentIntent(command))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        f.server().verify(); // ninguna expectativa: ninguna petición
    }

    @Test
    void succeededStatusMapsToApproved() {
        assertThat(StripePaymentIntentAdapter.mapStatus("succeeded")).isEqualTo(PaymentStatus.APPROVED);
        assertThat(StripePaymentIntentAdapter.mapStatus("canceled")).isEqualTo(PaymentStatus.DECLINED);
        assertThat(StripePaymentIntentAdapter.mapStatus(null)).isEqualTo(PaymentStatus.PENDING);
    }
}
