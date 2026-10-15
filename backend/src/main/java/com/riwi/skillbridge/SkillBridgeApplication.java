package com.riwi.skillbridge;

import com.riwi.skillbridge.infrastructure.config.ElevenLabsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// ElevenLabsProperties es un record inmutable: se registra explícitamente aquí porque
// no lleva @Component (a diferencia de AiProviderProperties, una clase mutable con
// setters que sí puede usar @Component + @ConfigurationProperties sin conflicto).
@SpringBootApplication
@EnableConfigurationProperties(ElevenLabsProperties.class)
public class SkillBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillBridgeApplication.class, args);
    }
}
