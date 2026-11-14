package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/** Simula el cable Kafka: mismo serializador del productor y mismo deserializador que la factoría de mensajería. */
public final class KafkaEnvelopeWire {

    /** Equivalente al ObjectMapper de Spring Boot que usan los consumidores para convertir el payload. */
    public static final ObjectMapper BOOT_LIKE_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private KafkaEnvelopeWire() {
    }

    public static byte[] serialize(String topic, BusinessEvent<?> event) {
        try (JsonSerializer<BusinessEvent<?>> serializer = new JsonSerializer<>()) {
            return serializer.serialize(topic, event);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BusinessEvent<?> deserialize(String topic, byte[] bytes) {
        try (JsonDeserializer<Object> deserializer = new JsonDeserializer(BusinessEvent.class, false)) {
            deserializer.addTrustedPackages("com.riwi.skillbridge.*");
            return (BusinessEvent<?>) deserializer.deserialize(topic, bytes);
        }
    }

    public static BusinessEvent<?> roundTrip(String topic, BusinessEvent<?> event) {
        return deserialize(topic, serialize(topic, event));
    }
}
