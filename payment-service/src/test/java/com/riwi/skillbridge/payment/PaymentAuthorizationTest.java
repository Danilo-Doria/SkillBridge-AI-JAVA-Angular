package com.riwi.skillbridge.payment;

import com.riwi.skillbridge.payment.application.port.in.SyncReadModelUseCase;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort.GatewayResult;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.MentorshipMode;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import com.riwi.skillbridge.payment.domain.model.PaymentStatus;
import com.riwi.skillbridge.payment.support.PaymentEventsProbe;
import com.riwi.skillbridge.payment.support.TestInfrastructure;
import com.riwi.skillbridge.payment.support.TestJwt;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Propiedad de la reserva con el read model local (FR-010) y reglas de autorización. Stripe sustituido por un mock. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentAuthorizationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry, true);
    }

    @Autowired MockMvc mvc;
    @Autowired SyncReadModelUseCase sync;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentGatewayPort gateway;

    private final UUID owner = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(gateway);
        when(gateway.isConfigured()).thenReturn(true);
        sync.offeringUpserted(new OfferingView(offeringId, UUID.randomUUID(), "Spring Boot 101", new BigDecimal("50.00"),
                true, MentorshipMode.LIVE, 2), UUID.randomUUID(), Instant.now());
    }

    private UUID seedBooking(UUID customer, String status, Instant scheduledAt, UUID offering) {
        UUID bookingId = UUID.randomUUID();
        sync.bookingCreated(new BookingView(bookingId, offering, customer, "owner@test.local", scheduledAt, status),
                UUID.randomUUID(), Instant.now());
        return bookingId;
    }

    private ResultActions authorize(UUID actor, UUID bookingId, String amount, String key) throws Exception {
        return mvc.perform(post("/api/payments/authorize")
                .header("Authorization", "Bearer " + TestJwt.token(actor, "actor@test.local", "CUSTOMER"))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":\"" + bookingId + "\",\"amount\":" + amount + "}"));
    }

    private int paymentRows(UUID bookingId) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_result WHERE booking_id = ?", Integer.class, bookingId);
    }

    @Test
    void nonOwnerIsDeniedWith404LikeTheMonolithAndNothingIsCharged() throws Exception {
        UUID bookingId = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), offeringId);

        authorize(UUID.randomUUID(), bookingId, "50.00", "intruder-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Reserva no encontrada"));

        verify(gateway, never()).createPaymentIntent(any());
        assertThat(paymentRows(bookingId)).isZero();
    }

    @Test
    void unknownBookingIsControlled409AndNeverAccepted() throws Exception {
        UUID bookingId = UUID.randomUUID();

        authorize(owner, bookingId, "50.00", "unknown-1")
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_SYNCHRONIZED"));

        verify(gateway, never()).createPaymentIntent(any());
        assertThat(paymentRows(bookingId)).isZero();
    }

    @Test
    void unknownOfferingIsControlled409() throws Exception {
        UUID bookingId = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), UUID.randomUUID());

        authorize(owner, bookingId, "50.00", "no-offering-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFERING_NOT_SYNCHRONIZED"));
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test
    void ownerGetsClientSecretAndIdempotencyKeyReplaysWithoutSecondCharge() throws Exception {
        UUID bookingId = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), offeringId);
        when(gateway.createPaymentIntent(any())).thenReturn(new GatewayResult(PaymentStatus.PENDING, "pi_test_123",
                "pi_test_123_secret_abc", "Stripe PaymentIntent created: pi_test_123"));

        for (int i = 0; i < 2; i++) {
            authorize(owner, bookingId, "50.00", "same-key")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.clientSecret").value("pi_test_123_secret_abc"));
        }
        verify(gateway, times(1)).createPaymentIntent(any());
        assertThat(paymentRows(bookingId)).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyForAnotherBookingIs409() throws Exception {
        UUID first = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), offeringId);
        UUID second = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), offeringId);
        when(gateway.createPaymentIntent(any())).thenReturn(new GatewayResult(PaymentStatus.PENDING, "pi_x", "pi_x_secret", "ok"));

        authorize(owner, first, "50.00", "reused-key").andExpect(status().isOk());
        authorize(owner, second, "50.00", "reused-key").andExpect(status().isConflict());
    }

    @Test
    void cancelledBookingIsDeclinedWithoutCallingStripe() throws Exception {
        UUID bookingId = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), offeringId);
        sync.bookingCancelled(bookingId, offeringId, owner, UUID.randomUUID(), Instant.now().plusSeconds(1));

        authorize(owner, bookingId, "50.00", "cancelled-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.message").value("La reserva ya se encuentra cancelada."));
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test
    void confirmedOrCompletedBookingIsDeclinedAsAlreadyPaid() throws Exception {
        for (String paidStatus : List.of("CONFIRMED", "COMPLETED")) {
            UUID bookingId = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), offeringId);
            sync.bookingStateChanged(new BookingView(bookingId, offeringId, owner, null,
                    Instant.now().plus(3, ChronoUnit.DAYS), paidStatus), UUID.randomUUID(), Instant.now().plusSeconds(1));

            authorize(owner, bookingId, "50.00", "paid-" + paidStatus)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DECLINED"))
                    .andExpect(jsonPath("$.message").value("La reserva ya está pagada."));
        }
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test
    void amountDifferentFromOfferingPriceIs422() throws Exception {
        UUID bookingId = seedBooking(owner, "CREATED", Instant.now().plus(3, ChronoUnit.DAYS), offeringId);

        authorize(owner, bookingId, "1.00", "cheap-1")
                .andExpect(status().isUnprocessableEntity());
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test
    void expiredPaymentWindowIsDeclinedAndPublishesPaymentRejected() throws Exception {
        UUID bookingId = seedBooking(owner, "CREATED", Instant.now().plus(2, ChronoUnit.HOURS), offeringId);

        authorize(owner, bookingId, "50.00", "late-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
        verify(gateway, never()).createPaymentIntent(any());

        List<ConsumerRecord<String, String>> records =
                PaymentEventsProbe.recordsWithKey(bookingId.toString(), Duration.ofSeconds(4));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).value()).contains("\"eventType\":\"PaymentRejected\"");
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/api/payments/authorize")
                        .header("Authorization", "Bearer " + TestJwt.token(owner, "o@test.local", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + UUID.randomUUID() + "\",\"amount\":50.00}"))
                .andExpect(status().isBadRequest());
    }
}
