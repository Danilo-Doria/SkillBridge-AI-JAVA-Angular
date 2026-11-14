package com.riwi.skillbridge.notification.integration;

import com.riwi.skillbridge.notification.application.port.out.EmailSenderPort;
import com.riwi.skillbridge.notification.domain.model.EmailContent;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import com.riwi.skillbridge.notification.support.MonolithEnvelopes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/** Flujo real Kafka -> listener -> Postgres (idempotencia) -> SMTP (Mailpit). */
class NotificationFlowIntegrationTest extends AbstractNotificationIntegrationTest {

    static final String TOPIC = "notification-requests-flow";
    static final String DLT = TOPIC + ".DLT";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        baseProperties(registry, TOPIC, MAILPIT.getMappedPort(1025));
    }

    @MockitoSpyBean
    EmailSenderPort emailSender;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private final Map<String, String> correlationAtSend = new ConcurrentHashMap<>();

    @BeforeEach
    void captureMdcAtSend() {
        doAnswer(invocation -> {
            EmailContent mail = invocation.getArgument(0);
            String mdc = MDC.get("correlationId");
            correlationAtSend.put(mail.to(), mdc == null ? "<none>" : mdc);
            return invocation.callRealMethod();
        }).when(emailSender).send(any());
    }

    @Test
    void duplicateEventSendsSingleEmail() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        String email = "dup-" + eventId + "@example.com";
        ProducerRecord<String, byte[]> record =
            enriched(TOPIC, eventId, booking, NotificationType.BOOKING_CREATED, "corr-dup", email);

        send(record);
        send(record);
        // Centinela con la misma clave (misma partición): cuando llega, ya se procesaron los dos duplicados.
        String sentinel = "sentinel-" + eventId + "@example.com";
        send(enriched(TOPIC, UUID.randomUUID(), booking, NotificationType.BOOKING_CANCELLED, "corr-s", sentinel));

        await().atMost(TIMEOUT).until(() -> mailsTo(sentinel).size() == 1);
        assertThat(mailsTo(email)).containsExactly("Reserva creada");
        assertThat(processedCount(eventId)).isEqualTo(1);
        verify(emailSender, org.mockito.Mockito.times(1)).send(argThat(m -> m.to().equals(email)));
    }

    @Test
    void brokenJsonGoesToDltWithoutBlockingPartition() throws Exception {
        String key = UUID.randomUUID().toString();
        send(new ProducerRecord<>(TOPIC, key, "{not-json".getBytes(StandardCharsets.UTF_8)));
        String email = "after-broken-" + key + "@example.com";
        send(enriched(TOPIC, UUID.randomUUID(), UUID.fromString(key), NotificationType.BOOKING_CREATED,
            "corr-after", email));

        await().atMost(TIMEOUT).until(() -> mailsTo(email).size() == 1);
        ConsumerRecord<String, byte[]> dlt = awaitRecord(DLT, r -> key.equals(r.key()), TIMEOUT).orElseThrow();
        assertThat(new String(dlt.value(), StandardCharsets.UTF_8)).isEqualTo("{not-json");
        assertThat(header(dlt, "kafka_dlt-exception-fqcn")).contains("DeserializationException");
        assertThat(header(dlt, "kafka_dlt-original-topic")).isEqualTo(TOPIC);
    }

    @Test
    void correlationIdIsRestoredInMdcWhileSending() throws Exception {
        UUID eventId = UUID.randomUUID();
        String email = "corr-" + eventId + "@example.com";
        String correlationId = "corr-" + UUID.randomUUID();
        send(enriched(TOPIC, eventId, UUID.randomUUID(), NotificationType.BOOKING_CREATED, correlationId, email));

        await().atMost(TIMEOUT).until(() -> mailsTo(email).size() == 1);
        assertThat(correlationAtSend).containsEntry(email, correlationId);
    }

    @Test
    void currentMonolithMessageWithoutRecipientGoesToDltWithoutRetry() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        Instant now = Instant.now();
        send(MonolithEnvelopes.record(TOPIC, MonolithEnvelopes.envelope(eventId, booking,
            NotificationType.BOOKING_CREATED, now, "corr-norecipient",
            new MonolithEnvelopes.CurrentNotificationMessage(eventId, booking, UUID.randomUUID(),
                NotificationType.BOOKING_CREATED, now, null))));

        ConsumerRecord<String, byte[]> dlt =
            awaitRecord(DLT, r -> eventId.toString().equals(header(r, "eventId")), TIMEOUT).orElseThrow();
        assertThat(header(dlt, "kafka_dlt-exception-cause-fqcn")).contains("PermanentNotificationException");
        assertThat(header(dlt, "X-Correlation-Id")).isEqualTo("corr-norecipient");
        assertThat(processedCount(eventId)).isZero();
    }

    @Test
    void exposesOnlyActuator() {
        assertThat(handlerMapping.getHandlerMethods().values())
            .allSatisfy(handler -> assertThat(handler.getBeanType().getName())
                .startsWith("org.springframework.boot."));
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
