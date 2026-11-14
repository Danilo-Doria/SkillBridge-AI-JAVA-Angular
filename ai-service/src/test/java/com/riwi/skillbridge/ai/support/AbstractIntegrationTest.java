package com.riwi.skillbridge.ai.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL y Kafka reales (Testcontainers), compartidos por todos los contextos de prueba.
 * Sin claves de proveedor de IA: verifica que ai-service arranca sin ellas.
 */
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("skillbridge")
                    .withUsername("ai_service")
                    .withPassword("ai_service_test");

    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=ai");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.jwt.public-key", () -> TestJwt.PUBLIC_PEM);
        registry.add("app.jwt.key-id", () -> TestJwt.KEY_ID);
        // Sin claves de proveedor, aunque el entorno del desarrollador las tenga.
        registry.add("spring.ai.google.genai.api-key", () -> "");
        registry.add("spring.ai.openai.api-key", () -> "");
        registry.add("app.ai.groq.api-key", () -> "");
        registry.add("elevenlabs.api-key", () -> "");
    }
}
