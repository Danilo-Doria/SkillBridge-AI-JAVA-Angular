package com.riwi.skillbridge.payment.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Construye eventos de webhook con la forma de Stripe y los firma como Stripe (HMAC-SHA256 de "t.payload"). */
public final class StripeWebhookFixtures {

    private StripeWebhookFixtures() {
    }

    public static String paymentIntentEvent(String eventId, String type, String paymentIntentId, UUID bookingId,
                                            String failureMessage) {
        String error = failureMessage == null ? "null"
                : "{\"code\":\"card_declined\",\"message\":\"" + failureMessage + "\",\"type\":\"card_error\"}";
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2024-06-20",
                  "created": %d,
                  "livemode": false,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": 5000,
                      "currency": "usd",
                      "status": "%s",
                      "metadata": { "bookingId": "%s" },
                      "last_payment_error": %s
                    }
                  }
                }
                """.formatted(eventId, Instant.now().getEpochSecond(), type, paymentIntentId,
                type.endsWith("succeeded") ? "succeeded" : "requires_payment_method", bookingId, error);
    }

    public static String sign(String payload, String secret) {
        return sign(payload, secret, Instant.now().getEpochSecond());
    }

    public static String sign(String payload, String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
