package com.riwi.skillbridge.ai.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Consumo de {@code booking-events} para el read model (contracts/kafka-topics.md): un group-id propio
 * ({@code ai-service}), {@link DefaultErrorHandler} con {@link ExponentialBackOff} (3 reintentos, 1 s, x2) y
 * {@link DeadLetterPublishingRecoverer} hacia {@code ai-service.booking-events.DLT} (nombre propio para no chocar
 * con la DLT de otros consumidores del topic compartido). Los mensajes mal formados no se reintentan.
 *
 * <p>El productor de eventos de recomendación usa la configuración de Spring Boot ({@code JsonSerializer}).
 */
@Configuration
public class KafkaConfiguration {

    @Bean
    NewTopic offeringEventsDeadLetterTopic(@Value("${app.kafka.topic.booking-events-dlt}") String dlt) {
        return TopicBuilder.name(dlt).partitions(1).replicas(1).build();
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> offeringEventsListenerFactory(
            KafkaProperties kafkaProperties,
            @Value("${app.kafka.consumer.group-id:ai-service}") String groupId,
            @Value("${app.kafka.topic.booking-events-dlt}") String dlt) {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties(null);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties(null);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        KafkaTemplate<String, String> dltTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltTemplate,
                (record, ex) -> new TopicPartition(dlt, 0));
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, NullPointerException.class,
                JsonProcessingException.class, IOException.class, DateTimeParseException.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerProps));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
