package com.riwi.skillbridge.payment.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * PostgreSQL y Kafka reales (Testcontainers), compartidos por todas las clases de prueba. Stripe nunca se llama:
 * la URL de la API apunta a un puerto cerrado y los tests que necesitan la pasarela la sustituyen por un mock.
 * El secreto de firma del webhook se genera aleatoriamente en cada ejecución.
 */
public final class TestInfrastructure {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("skillbridge")
                    .withUsername("payment_svc")
                    .withPassword("payment_svc_test");

    public static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    /** Secreto de prueba generado en el test (nunca una clave real). */
    public static final String WEBHOOK_SECRET = "whsec_test_" + randomHex(24);
    public static final String SECRET_KEY = "sk_test_" + randomHex(12);

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    private TestInfrastructure() {
    }

    public static void register(DynamicPropertyRegistry registry, boolean withStripeKeys) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=payment");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.jwt.public-key", () -> TestJwt.PUBLIC_PEM);
        registry.add("app.jwt.key-id", () -> TestJwt.KEY_ID);
        registry.add("stripe.api-url", () -> "http://127.0.0.1:9");
        registry.add("stripe.secret-key", () -> withStripeKeys ? SECRET_KEY : "");
        registry.add("stripe.webhook-secret", () -> withStripeKeys ? WEBHOOK_SECRET : "");
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
