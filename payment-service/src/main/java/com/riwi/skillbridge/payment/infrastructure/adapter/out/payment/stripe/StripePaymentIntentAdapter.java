package com.riwi.skillbridge.payment.infrastructure.adapter.out.payment.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.domain.exception.PaymentGatewayUnavailableException;
import com.riwi.skillbridge.payment.domain.model.PaymentCommand;
import com.riwi.skillbridge.payment.domain.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Crea PaymentIntents en Stripe ({@code POST /v1/payment_intents}) con los mismos parámetros que
 * {@code StripePaymentAdapter} del monolito. Solo envía importe, moneda, descripción y metadatos (bookingId,
 * idempotencyKey): ningún dato de tarjeta, que el navegador entrega directamente a Stripe Elements.
 *
 * <p>Errores: 4xx de negocio (p. ej. 400/402) → DECLINED; credencial rechazada (401/403), 429, 5xx o red → 503.
 */
@Component
public class StripePaymentIntentAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentIntentAdapter.class);

    private final RestClient stripe;
    private final StripeProperties properties;

    public StripePaymentIntentAdapter(@Qualifier("stripeRestClient") RestClient stripe, StripeProperties properties) {
        this.stripe = stripe;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.hasSecretKey();
    }

    @Override
    public GatewayResult createPaymentIntent(PaymentCommand command) {
        if (!isConfigured()) {
            throw new PaymentGatewayUnavailableException("La pasarela de pagos no está configurada.");
        }
        long amountInCents = command.amount().setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).longValueExact();
        log.info("Creating Stripe PaymentIntent for bookingId={}, amountInCents={}", command.bookingId(), amountInCents);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(amountInCents));
        form.add("currency", properties.currency());
        form.add("description", "SkillBridge Booking " + command.bookingId());
        form.add("metadata[bookingId]", command.bookingId().toString());
        form.add("metadata[idempotencyKey]", command.idempotencyKey());
        form.add("automatic_payment_methods[enabled]", "true");

        try {
            PaymentIntentResponse response = stripe.post()
                .uri("/v1/payment_intents")
                .headers(h -> {
                    h.setBearerAuth(properties.secretKey());
                    h.set("Idempotency-Key", command.idempotencyKey());
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(PaymentIntentResponse.class);
            if (response == null || response.id() == null) {
                throw new PaymentGatewayUnavailableException("Respuesta vacía de la pasarela de pagos.");
            }
            log.info("Stripe PaymentIntent created: id={}, status={}", response.id(), response.status());
            return new GatewayResult(mapStatus(response.status()), response.id(), response.clientSecret(),
                "Stripe PaymentIntent created: " + response.id());
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN
                || status == HttpStatus.TOO_MANY_REQUESTS) {
                log.error("Stripe rejected the request for bookingId={} with HTTP {}", command.bookingId(), e.getStatusCode().value());
                throw new PaymentGatewayUnavailableException("La pasarela de pagos no está disponible.", e);
            }
            String message = errorMessage(e);
            log.warn("Stripe declined PaymentIntent for bookingId={} with HTTP {}: {}", command.bookingId(),
                e.getStatusCode().value(), message);
            return new GatewayResult(PaymentStatus.DECLINED, null, null, "Payment gateway error: " + message);
        } catch (RestClientException e) {
            log.error("Stripe unavailable for bookingId={}: {}", command.bookingId(), e.getClass().getSimpleName());
            throw new PaymentGatewayUnavailableException("La pasarela de pagos no está disponible.", e);
        }
    }

    private static String errorMessage(HttpClientErrorException e) {
        try {
            StripeErrorResponse body = e.getResponseBodyAs(StripeErrorResponse.class);
            if (body != null && body.error() != null && body.error().message() != null) {
                return body.error().message();
            }
        } catch (RuntimeException ignored) {
            // cuerpo no JSON: se usa el código HTTP
        }
        return "HTTP " + e.getStatusCode().value();
    }

    static PaymentStatus mapStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return PaymentStatus.PENDING;
        }
        return switch (stripeStatus.toLowerCase()) {
            case "succeeded" -> PaymentStatus.APPROVED;
            case "canceled" -> PaymentStatus.DECLINED;
            default -> PaymentStatus.PENDING;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentIntentResponse(String id, String status, @JsonProperty("client_secret") String clientSecret) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StripeErrorResponse(StripeError error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StripeError(String code, String message, String type) {}
}
