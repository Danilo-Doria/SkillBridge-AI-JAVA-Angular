package com.riwi.skillbridge.payment.infrastructure.config;

import com.riwi.skillbridge.payment.infrastructure.adapter.out.payment.stripe.StripeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class PaymentInfrastructureConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaymentInfrastructureConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Cliente HTTP de Stripe con timeouts. La clave secreta se añade por petición (nunca se registra). */
    @Bean
    RestClient stripeRestClient(RestClient.Builder builder, StripeProperties properties) {
        if (!properties.hasSecretKey()) {
            log.warn("STRIPE_SECRET_KEY no configurada: /api/payments/** responderá 503 hasta que se configure");
        }
        if (!properties.hasWebhookSecret()) {
            log.warn("STRIPE_WEBHOOK_SECRET no configurado: /api/webhooks/stripe responderá 503 hasta que se configure");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return builder.clone().baseUrl(properties.apiUrl()).requestFactory(factory).build();
    }
}
