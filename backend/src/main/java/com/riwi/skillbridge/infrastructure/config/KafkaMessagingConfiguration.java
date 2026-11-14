package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.application.port.out.PermanentNotificationException;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.KafkaEventHeaders;
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
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-104 (US4) — Kafka como broker de pagos y notificaciones. Contrato en
 * {@code specs/104-microservices-gateway/contracts/kafka-topics.md}.
 *
 * <ul>
 *   <li>Topics {@code payment-events}, {@code notification-requests} y sus {@code .DLT} (según flag).</li>
 *   <li>Factoría de listeners propia: {@link ErrorHandlingDeserializer} (un mensaje ilegible no bloquea la
 *       partición), {@link DefaultErrorHandler} con {@link ExponentialBackOffWithMaxRetries} (3 reintentos, 1 s, x2) y
 *       {@link DeadLetterPublishingRecoverer} hacia {@code <topic>.DLT}.</li>
 * </ul>
 * Los consumidores existentes de {@code booking-events} (audit/analytics) siguen en la factoría por defecto.
 */
@Configuration
public class KafkaMessagingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagingConfiguration.class);

    public static final String LISTENER_FACTORY = "messagingKafkaListenerContainerFactory";

    // ── Topics ──────────────────────────────────────────────────────────────

    @Bean
    NewTopic paymentEventsTopic(@Value("${app.kafka.topic.payment-events:payment-events}") String topic,
                                @Value("${app.messaging.kafka.partitions:3}") int partitions,
                                @Value("${app.messaging.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic paymentEventsDltTopic(@Value("${app.kafka.topic.payment-events:payment-events}") String topic,
                                   @Value("${app.messaging.kafka.dlt-partitions:1}") int partitions,
                                   @Value("${app.messaging.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(topic + KafkaEventHeaders.DLT_SUFFIX).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic notificationRequestsTopic(@Value("${app.kafka.topic.notification-requests:notification-requests}") String topic,
                                       @Value("${app.messaging.kafka.partitions:3}") int partitions,
                                       @Value("${app.messaging.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic notificationRequestsDltTopic(@Value("${app.kafka.topic.notification-requests:notification-requests}") String topic,
                                          @Value("${app.messaging.kafka.dlt-partitions:1}") int partitions,
                                          @Value("${app.messaging.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(topic + KafkaEventHeaders.DLT_SUFFIX).partitions(partitions).replicas(replicas).build();
    }

    // ── Consumo con DLT ─────────────────────────────────────────────────────
    // Ni la ConsumerFactory, ni la plantilla de DLT, ni el error handler se exponen como beans: Spring Boot
    // los recogería (ConditionalOnMissingBean / CommonErrorHandler único) y cambiaría el comportamiento
    // de la factoría por defecto que usan audit y analytics.

    @Bean(LISTENER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, Object> messagingKafkaListenerContainerFactory(
            ConsumerFactory<?, ?> kafkaConsumerFactory,
            ProducerFactory<?, ?> kafkaProducerFactory,
            @Value("${app.messaging.kafka.retry.max-retries:3}") int maxRetries,
            @Value("${app.messaging.kafka.retry.initial-interval:1000}") long initialInterval,
            @Value("${app.messaging.kafka.retry.multiplier:2.0}") double multiplier,
            @Value("${app.messaging.kafka.retry.max-interval:10000}") long maxInterval) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(messagingConsumerFactory(kafkaConsumerFactory.getConfigurationProperties()));
        factory.setCommonErrorHandler(errorHandler(
            deadLetterTemplate(kafkaProducerFactory.getConfigurationProperties()),
            backOff(maxRetries, initialInterval, multiplier, maxInterval)));
        return factory;
    }

    /** Hereda bootstrap/seguridad de Boot pero fija los deserializadores (ignora spring.kafka.consumer.*-deserializer). */
    static ConsumerFactory<String, Object> messagingConsumerFactory(Map<String, Object> bootProps) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        JsonDeserializer<Object> json = new JsonDeserializer(BusinessEvent.class, false);
        json.addTrustedPackages("com.riwi.skillbridge.*");
        return new DefaultKafkaConsumerFactory<>(withoutSerdeSettings(bootProps),
            new ErrorHandlingDeserializer<>(new StringDeserializer()),
            new ErrorHandlingDeserializer<>(json));
    }

    /**
     * Plantilla solo para la DLT: reenvía {@code byte[]} tal cual (mensajes que ni se pudieron deserializar)
     * y el resto como JSON. El recoverer conserva las cabeceras originales ({@code X-Correlation-Id}, {@code eventId}...)
     * y añade {@code kafka_dlt-*} (excepción, topic/partición/offset de origen).
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

    /**
     * Copia la configuración de Boot (bootstrap, seguridad, auto-offset-reset...) sin las claves de (de)serialización:
     * aquí se usan instancias explícitas y {@code spring.json.*} chocaría con su configuración por setters.
     */
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
        handler.addNotRetryableExceptions(
            BusinessRuleException.class,
            DomainNotFoundException.class,
            PermanentNotificationException.class,
            IllegalArgumentException.class);
        return handler;
    }
}
