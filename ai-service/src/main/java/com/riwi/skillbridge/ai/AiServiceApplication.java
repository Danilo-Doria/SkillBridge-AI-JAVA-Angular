package com.riwi.skillbridge.ai;

import com.riwi.skillbridge.ai.infrastructure.config.ElevenLabsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** ai-service (HU-104 / US5): recomendaciones, visión, voz a texto y texto a voz fuera del monolito. */
@SpringBootApplication
@EnableConfigurationProperties(ElevenLabsProperties.class)
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
