package com.riwi.skillbridge.payment.infrastructure.adapter.out.payment.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuración de Stripe. Las claves llegan por variable de entorno/secreto; nunca se registran en logs. */
@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
    String apiUrl,
    String secretKey,
    String webhookSecret,
    String currency,
    Long webhookToleranceSeconds,
    Duration connectTimeout,
    Duration readTimeout
) {
    public StripeProperties {
        apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.stripe.com" : apiUrl;
        currency = currency == null || currency.isBlank() ? "usd" : currency.toLowerCase();
        webhookToleranceSeconds = webhookToleranceSeconds == null ? 300L : webhookToleranceSeconds;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
    }

    public boolean hasSecretKey() {
        return secretKey != null && !secretKey.isBlank();
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    @Override
    public String toString() {
        return "StripeProperties[apiUrl=" + apiUrl + ", currency=" + currency + ", secretKey="
            + (hasSecretKey() ? "***" : "<empty>") + ", webhookSecret=" + (hasWebhookSecret() ? "***" : "<empty>") + "]";
    }
}
