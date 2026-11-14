package com.riwi.skillbridge.notification.integration;

import com.riwi.skillbridge.notification.application.port.out.EmailSenderPort;
import com.riwi.skillbridge.notification.domain.model.NotificationType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** SMTP caído (puerto cerrado): 1 intento + 3 reintentos (1 s, 2 s, 4 s) y después DLT con cabeceras. */
class SmtpFailureIntegrationTest extends AbstractNotificationIntegrationTest {

    static final String TOPIC = "notification-requests-smtpfail";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        baseProperties(registry, TOPIC, closedPort);
        registry.add("spring.mail.host", () -> "127.0.0.1");
    }

    @MockitoSpyBean
    EmailSenderPort emailSender;

    @Test
    void smtpFailureIsRetriedThenSentToDltWithHeaders() throws Exception {
        UUID eventId = UUID.randomUUID();
        String email = "smtp-down-" + eventId + "@example.com";
        String correlationId = "corr-smtp-" + eventId;
        long start = System.nanoTime();
        send(enriched(TOPIC, eventId, UUID.randomUUID(), NotificationType.BOOKING_CREATED, correlationId, email));

        ConsumerRecord<String, byte[]> dlt = awaitRecord(TOPIC + ".DLT",
            r -> eventId.toString().equals(header(r, "eventId")), Duration.ofSeconds(60)).orElseThrow();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        verify(emailSender, times(4)).send(argThat(m -> m.to().equals(email)));
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(6500)); // 1 s + 2 s + 4 s de backoff
        assertThat(header(dlt, "X-Correlation-Id")).isEqualTo(correlationId);
        assertThat(header(dlt, "eventType")).isEqualTo("BookingCreatedNotification");
        assertThat(header(dlt, "schemaVersion")).isEqualTo("1");
        assertThat(header(dlt, "kafka_dlt-original-topic")).isEqualTo(TOPIC);
        assertThat(header(dlt, "kafka_dlt-exception-cause-fqcn")).isEqualTo("org.springframework.mail.MailSendException");
        assertThat(processedCount(eventId)).isZero(); // la marca de idempotencia se revirtió
    }
}
