package com.riwi.skillbridge.notification.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.notification.domain.exception.PermanentNotificationException;
import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.BusinessEventEnvelope;
import com.riwi.skillbridge.notification.infrastructure.adapter.in.messaging.KafkaEventHeaders;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Política de consumo del contrato {@code kafka-topics.md}, igual que {@code KafkaMessagingConfiguration}
 * del monolito:
 * <ul>
 *   <li>{@link ErrorHandlingDeserializer} sobre {@link JsonDeserializer} del envelope, ignorando la cabecera
 *       {@code __TypeId__} (que trae la clase del monolito): un mensaje ilegible no bloquea la partición.</li>
 *   <li>{@link DefaultErrorHandler} con {@link ExponentialBackOffWithMaxRetries} (3 reintentos, 1 s, x2) y
 *       {@link DeadLetterPublishingRecoverer} hacia {@code <topic>.DLT}.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    public static final String LISTENER_FACTORY = "notificationKafkaListenerContainerFactory";

    // Topics: idempotente si el monolito ya los creó (KafkaAdmin no recrea los existentes).
    @Bean
    NewTopic notificationRequestsTopic(@Value("${notification.kafka.topic:notification-requests}") String topic,
                                       @Value("${notification.kafka.partitions:3}") int partitions,
                                       @Value("${notification.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic notificationRequestsDltTopic(@Value("${notification.kafka.topic:notification-requests}") String topic,
                                          @Value("${notification.kafka.dlt-partitions:1}") int partitions,
                                          @Value("${notification.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(topic + KafkaEventHeaders.DLT_SUFFIX).partitions(partitions).replicas(replicas).build();
    }

    @Bean(LISTENER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, BusinessEventEnvelope> notificationKafkaListenerContainerFactory(
            ConsumerFactory<?, ?> kafkaConsumerFactory,
            ProducerFactory<?, ?> kafkaProducerFactory,
            ObjectMapper objectMapper,
            @Value("${notification.kafka.retry.max-retries:3}") int maxRetries,
            @Value("${notification.kafka.retry.initial-interval:1000}") long initialInterval,
            @Value("${notification.kafka.retry.multiplier:2.0}") double multiplier,
            @Value("${notification.kafka.retry.max-interval:10000}") long maxInterval) {
        ConcurrentKafkaListenerContainerFactory<String, BusinessEventEnvelope> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(kafkaConsumerFactory.getConfigurationProperties(), objectMapper));
        factory.setCommonErrorHandler(errorHandler(
            deadLetterTemplate(kafkaProducerFactory.getConfigurationProperties()),
            backOff(maxRetries, initialInterval, multiplier, maxInterval)));
        return factory;
    }

    /** Deserializador del envelope: el mismo que usa el listener (reutilizado en el test de contrato). */
    public static ErrorHandlingDeserializer<BusinessEventEnvelope> envelopeDeserializer(ObjectMapper objectMapper) {
        JsonDeserializer<BusinessEventEnvelope> json =
            new JsonDeserializer<>(BusinessEventEnvelope.class, objectMapper, false);
        return new ErrorHandlingDeserializer<>(json);
    }

    static ConsumerFactory<String, BusinessEventEnvelope> consumerFactory(Map<String, Object> bootProps,
                                                                          ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(withoutSerdeSettings(bootProps),
            new ErrorHandlingDeserializer<>(new StringDeserializer()), envelopeDeserializer(objectMapper));
    }

    /**
     * Plantilla solo para la DLT: reenvía {@code byte[]} tal cual (mensajes que no se pudieron deserializar) y el
     * resto como JSON. El recoverer conserva las cabeceras originales y añade {@code kafka_dlt-*}.
     */
    static KafkaTemplate<Object, Object> deadLetterTemplate(Map<String, Object> bootProps) {
        Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
        byType.put(byte[].class, new ByteArraySerializer());
        byType.put(Object.class, new JsonSerializer<>());
        @SuppressWarnings({"unchecked", "rawtypes"})
        Serializer<Object> keySerializer = (Serializer) new StringSerializer();
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
            withoutSerdeSettings(bootProps), keySerializer, new DelegatingByTypeSerializer(byType, true));
        return new KafkaTemplate<>(factory);
    }

    static Map<String, Object> withoutSerdeSettings(Map<String, Object> bootProps) {
        Map<String, Object> props = new LinkedHashMap<>(bootProps);
        props.keySet().removeIf(key -> key.startsWith("spring.json.")
            || key.startsWith("spring.deserializer.") || key.startsWith("spring.serializer."));
        return props;
    }

    static ExponentialBackOffWithMaxRetries backOff(int maxRetries, long initialInterval, double multiplier,
                                                    long maxInterval) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxInterval);
        return backOff;
    }

    static DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> deadLetterTemplate,
                                            ExponentialBackOffWithMaxRetries backOff) {
        // Partición -1: la DLT tiene 1 partición aunque el origen tenga 3; que elija el productor.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate,
            (record, ex) -> {
                log.error("Sending to DLT topic={} partition={} offset={} key={} cause={}",
                    record.topic(), record.partition(), record.offset(), record.key(), ex.getMessage());
                return new TopicPartition(record.topic() + KafkaEventHeaders.DLT_SUFFIX, -1);
            });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Errores deterministas: reintentar no los arregla, van directos a la DLT.
        handler.addNotRetryableExceptions(PermanentNotificationException.class, IllegalArgumentException.class);
        return handler;
    }
}
