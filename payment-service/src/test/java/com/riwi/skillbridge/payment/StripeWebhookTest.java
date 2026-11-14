package com.riwi.skillbridge.payment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riwi.skillbridge.payment.application.port.in.SyncReadModelUseCase;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.MentorshipMode;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import com.riwi.skillbridge.payment.support.MonolithPaymentApproved;
import com.riwi.skillbridge.payment.support.PaymentEventsProbe;
import com.riwi.skillbridge.payment.support.TestInfrastructure;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.riwi.skillbridge.payment.support.StripeWebhookFixtures.paymentIntentEvent;
import static com.riwi.skillbridge.payment.support.StripeWebhookFixtures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Webhook de Stripe: firma antes de cualquier efecto, idempotencia por id de evento y contrato de payment-events. */
@SpringBootTest
@AutoConfigureMockMvc
class StripeWebhookTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry, true);
    }

    @Autowired MockMvc mvc;
    @Autowired SyncReadModelUseCase sync;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentGatewayPort gateway; // nunca se llama a Stripe real

    private ResultActions post(String payload, String signature) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("X-Correlation-Id", "corr-webhook-1");
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        return mvc.perform(request);
    }

    private int processedRows(UUID bookingId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_stripe_event WHERE booking_id = ?", Integer.class,
                bookingId);
    }

    @Test
    void invalidSignatureIs400WithoutAnyEffect() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String payload = paymentIntentEvent("evt_bad_" + bookingId, "payment_intent.succeeded", "pi_bad", bookingId, null);

        post(payload, sign(payload, "whsec_not_the_configured_secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid webhook signature"));
        // Cuerpo alterado tras firmar con el secreto correcto
        String signed = sign(payload, TestInfrastructure.WEBHOOK_SECRET);
        post(payload.replace("succeeded", "succeeded "), signed).andExpect(status().isBadRequest());
        // Firma caducada (fuera de la tolerancia de 300 s)
        post(payload, sign(payload, TestInfrastructure.WEBHOOK_SECRET, Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond()))
                .andExpect(status().isBadRequest());
        post(payload, null).andExpect(status().isBadRequest());

        assertThat(processedRows(bookingId)).isZero();
        assertThat(PaymentEventsProbe.recordsWithKey(bookingId.toString(), Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void duplicateWebhookPublishesExactlyOneEventWithMonolithShape() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID offering = UUID.randomUUID();
        UUID provider = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        sync.offeringUpserted(new OfferingView(offering, provider, "Kafka en serio", new BigDecimal("80.00"), true,
                MentorshipMode.ASYNCHRONOUS, 3), UUID.randomUUID(), Instant.now());
        sync.bookingCreated(new BookingView(bookingId, offering, customer, "cliente@test.local", scheduledAt, "CREATED"),
                UUID.randomUUID(), Instant.now());

        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String payload = paymentIntentEvent(eventId, "payment_intent.succeeded", "pi_ok_1", bookingId, null);
        for (int i = 0; i < 3; i++) {
            post(payload, sign(payload, TestInfrastructure.WEBHOOK_SECRET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.received").value(true));
        }
        assertThat(processedRows(bookingId)).isEqualTo(1);

        List<ConsumerRecord<String, String>> records =
                PaymentEventsProbe.recordsWithKey(bookingId.toString(), Duration.ofSeconds(5));
        assertThat(records).hasSize(1);
        ConsumerRecord<String, String> record = records.get(0);

        // Cabeceras del contrato (KafkaEventHeaders del monolito) + __TypeId__ del monolito.
        assertThat(header(record, "eventType")).isEqualTo("PaymentApproved");
        assertThat(header(record, "schemaVersion")).isEqualTo("2");
        assertThat(header(record, "X-Correlation-Id")).isEqualTo("corr-webhook-1");
        assertThat(header(record, "eventId")).isNotBlank();
        assertThat(header(record, "__TypeId__")).isEqualTo("com.riwi.skillbridge.application.port.out.event.BusinessEvent");

        // Deserialización exactamente como KafkaPaymentResultConsumer: JsonDeserializer(BusinessEvent, sin cabeceras de
        // tipo) y convertValue del payload a PaymentApproved del monolito (aquí con FAIL_ON_UNKNOWN_PROPERTIES=true,
        // para demostrar que no sobra ningún campo).
        try (JsonDeserializer<BusinessEvent<?>> deserializer = monolithDeserializer()) {
            BusinessEvent<?> event = deserializer.deserialize("payment-events", record.value().getBytes(StandardCharsets.UTF_8));
            assertThat(event.eventType()).isEqualTo("PaymentApproved");
            assertThat(event.aggregateId()).isEqualTo(bookingId.toString());
            assertThat(event.aggregateType()).isEqualTo("Payment");
            assertThat(event.version()).isEqualTo(2);
            assertThat(event.eventId().toString()).isEqualTo(header(record, "eventId"));

            ObjectMapper strict = new ObjectMapper().registerModule(new JavaTimeModule())
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            MonolithPaymentApproved approved = strict.convertValue(event.payload(), MonolithPaymentApproved.class);
            assertThat(approved.bookingId()).isEqualTo(bookingId);
            assertThat(approved.version()).isEqualTo(2);
            assertThat(approved.customerId()).isEqualTo(customer);
            assertThat(approved.customerEmail()).isEqualTo("cliente@test.local");
            assertThat(approved.offeringId()).isEqualTo(offering);
            assertThat(approved.offeringTitle()).isEqualTo("Kafka en serio");
            assertThat(approved.providerId()).isEqualTo(provider);
            assertThat(approved.scheduledAt()).isEqualTo(scheduledAt);
            assertThat(approved.durationHours()).isEqualTo(3);
            assertThat(approved.mode()).isEqualTo(MonolithPaymentApproved.Mode.ASYNCHRONOUS);
            assertThat(approved.paymentId()).isNull(); // sin autorización previa conocida, como el monolito
        }

        // Forma JSON: exactamente los 14 campos de docs/contracts/mentorships/events.md §2.
        JsonNode payloadJson = new ObjectMapper().readTree(record.value()).get("payload");
        assertThat(fieldNames(payloadJson)).isEqualTo(fieldNames(contractFixture()));
        assertThat(record.value()).doesNotContainIgnoringCase("card").doesNotContainIgnoringCase("cvc");
    }

    @Test
    void failedPaymentPublishesPaymentRejectedReadableByMonolith() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String payload = paymentIntentEvent("evt_fail_" + bookingId, "payment_intent.payment_failed", "pi_fail_1",
                bookingId, "Your card was declined.");

        post(payload, sign(payload, TestInfrastructure.WEBHOOK_SECRET)).andExpect(status().isOk());

        List<ConsumerRecord<String, String>> records =
                PaymentEventsProbe.recordsWithKey(bookingId.toString(), Duration.ofSeconds(4));
        assertThat(records).hasSize(1);
        try (JsonDeserializer<BusinessEvent<?>> deserializer = monolithDeserializer()) {
            BusinessEvent<?> event = deserializer.deserialize("payment-events", records.get(0).value().getBytes(StandardCharsets.UTF_8));
            assertThat(event.eventType()).isEqualTo("PaymentRejected");
            MonolithPaymentApproved.Rejected rejected = new ObjectMapper()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .convertValue(event.payload(), MonolithPaymentApproved.Rejected.class);
            assertThat(rejected.bookingId()).isEqualTo(bookingId);
            assertThat(rejected.reason()).isEqualTo("Your card was declined.");
        }
    }

    @Test
    void unrelatedEventTypesAreAcknowledgedWithoutEffects() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String payload = paymentIntentEvent("evt_created_" + bookingId, "payment_intent.created", "pi_c", bookingId, null);

        post(payload, sign(payload, TestInfrastructure.WEBHOOK_SECRET)).andExpect(status().isOk());

        assertThat(processedRows(bookingId)).isZero();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonDeserializer<BusinessEvent<?>> monolithDeserializer() {
        JsonDeserializer deserializer = new JsonDeserializer(BusinessEvent.class, false);
        deserializer.addTrustedPackages("com.riwi.skillbridge.*");
        return deserializer;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode contractFixture() throws Exception {
        try (InputStream in = StripeWebhookTest.class.getResourceAsStream("/contracts/payment-approved-v2.json")) {
            return new ObjectMapper().readTree(in);
        }
    }
}
