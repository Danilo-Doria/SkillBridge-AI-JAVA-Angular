package com.riwi.skillbridge.infrastructure.adapter.out.payment.stripe;

import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adaptador de salida para la pasarela real de Stripe implementando PaymentPort.
 * Integra OpenFeign y patrones de tolerancia a fallos con Resilience4j.
 */
@Component("stripePaymentAdapter")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe", matchIfMissing = true)
public class StripePaymentAdapter implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentAdapter.class);

    private final StripeFeignClient stripeFeignClient;
    private final String currency;

    public StripePaymentAdapter(
            StripeFeignClient stripeFeignClient,
            @Value("${stripe.currency:usd}") String currency) {
        this.stripeFeignClient = stripeFeignClient;
        this.currency = currency;
    }

    @Override
    @CircuitBreaker(name = "stripePaymentClient", fallbackMethod = "fallbackProcess")
    @Retry(name = "stripePaymentClient")
    public PaymentResult process(PaymentCommand command) {
        log.info("Processing Stripe PaymentIntent for bookingId={}, amount={}",
                command.bookingId(), command.amount());

        // Stripe requiere montos en centavos para monedas estándar como USD (ej: 50.00 USD -> 5000)
        long amountInCents = command.amount().multiply(BigDecimal.valueOf(100)).longValue();

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("amount", String.valueOf(amountInCents));
        formParams.add("currency", currency.toLowerCase());
        formParams.add("description", "SkillBridge Booking " + command.bookingId());
        formParams.add("metadata[bookingId]", command.bookingId().toString());
        formParams.add("metadata[idempotencyKey]", command.idempotencyKey());
        formParams.add("automatic_payment_methods[enabled]", "true");

        StripePaymentIntentResponse response = stripeFeignClient.createPaymentIntent(
                command.idempotencyKey(),
                formParams
        );

        log.info("Stripe PaymentIntent created successfully: id={}, status={}", response.id(), response.status());

        PaymentStatus status = mapStripeStatus(response.status());

        return new PaymentResult(
                UUID.randomUUID(),
                command.bookingId(),
                status,
                "Stripe PaymentIntent created: " + response.id(),
                response.clientSecret()
        );
    }

    /**
     * Fallback ante fallos continuos de red, timeouts o apertura de Circuit Breaker.
     */
    public PaymentResult fallbackProcess(PaymentCommand command, Throwable ex) {
        log.error("Fallback triggered for Stripe PaymentIntent creation. bookingId={}, reason={}",
                command.bookingId(), ex.getMessage());
        return new PaymentResult(
                UUID.randomUUID(),
                command.bookingId(),
                PaymentStatus.DECLINED,
                "Payment gateway unavailable (Circuit Breaker / Timeout): " + ex.getMessage(),
                null
        );
    }

    private PaymentStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return PaymentStatus.PENDING;
        }
        return switch (stripeStatus.toLowerCase()) {
            case "succeeded" -> PaymentStatus.APPROVED;
            case "canceled" -> PaymentStatus.DECLINED;
            default -> PaymentStatus.PENDING;
        };
    }
}
