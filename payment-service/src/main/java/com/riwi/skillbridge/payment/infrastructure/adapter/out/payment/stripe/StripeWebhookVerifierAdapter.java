package com.riwi.skillbridge.payment.infrastructure.adapter.out.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.payment.application.port.out.WebhookVerifierPort;
import com.riwi.skillbridge.payment.domain.exception.InvalidWebhookException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifica la firma {@code Stripe-Signature} con el SDK oficial ({@code Webhook.Signature.verifyHeader}: HMAC-SHA256
 * sobre {@code timestamp.cuerpoCrudo} y tolerancia de tiempo) y, solo después, lee del cuerpo los campos mínimos.
 * No usa la deserialización de objetos del SDK (sensible a la versión de API) ni registra el cuerpo: un
 * {@code last_payment_error} puede traer datos del medio de pago y solo se lee su {@code message}.
 */
@Component
public class StripeWebhookVerifierAdapter implements WebhookVerifierPort {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookVerifierAdapter.class);

    private final StripeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripeWebhookVerifierAdapter(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.hasWebhookSecret();
    }

    @Override
    public VerifiedWebhookEvent verify(String rawPayload, String signatureHeader) {
        if (rawPayload == null || signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookException("Invalid webhook signature");
        }
        try {
            Webhook.Signature.verifyHeader(rawPayload, signatureHeader, properties.webhookSecret(),
                properties.webhookToleranceSeconds());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe signature verification failed: {}", e.getMessage());
            throw new InvalidWebhookException("Invalid webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode object = root.path("data").path("object");
            String eventId = text(root, "id");
            String type = text(root, "type");
            if (eventId == null || type == null) {
                throw new InvalidWebhookException("Webhook processing failed");
            }
            return new VerifiedWebhookEvent(eventId, type, text(object, "object"), text(object, "id"),
                text(object.path("metadata"), "bookingId"), text(object.path("last_payment_error"), "message"));
        } catch (InvalidWebhookException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Signed Stripe webhook body could not be parsed: {}", e.getClass().getSimpleName());
            throw new InvalidWebhookException("Webhook processing failed");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() || v.isMissingNode() ? null : v.asText();
    }
}
