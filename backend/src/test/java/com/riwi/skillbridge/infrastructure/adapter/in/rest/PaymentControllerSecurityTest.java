package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.PaymentAuthorizationService;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.AuthCookieService;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-003: cadena real (seguridad + controlador + servicio de pagos). Solo se simulan
 * los puertos de salida y la tabla de usuarios.
 */
@WebMvcTest(
    controllers = PaymentController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class,
    PaymentAuthorizationService.class, CurrentActorResolver.class})
class PaymentControllerSecurityTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();

    @Autowired MockMvc mvc;

    @MockitoBean PaymentPort paymentPort;
    @MockitoBean PaymentResultRepositoryPort paymentResults;
    @MockitoBean PaymentEventPublisherPort eventPublisher;
    @MockitoBean BookingRepositoryPort bookings;
    @MockitoBean UserAccountPort accounts;
    // Dependencias del filtro JWT (aquí la autenticación la simula @WithMockUser)
    @MockitoBean JwtService jwtService;
    @MockitoBean DatabaseUserDetailsService userDetailsService;
    @MockitoBean AuthCookieService cookieService;

    @BeforeEach
    void setUp() {
        when(accounts.findIdByEmail("owner@test.com")).thenReturn(Optional.of(OWNER_ID));
        when(accounts.findIdByEmail("other@test.com")).thenReturn(Optional.of(OTHER_ID));
        when(accounts.findIdByEmail("admin@test.com")).thenReturn(Optional.of(UUID.randomUUID()));
        when(accounts.findIdByEmail("provider@test.com")).thenReturn(Optional.of(UUID.randomUUID()));
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(bookingOf(OWNER_ID)));
        when(paymentResults.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "CUSTOMER")
    void dueno_de_la_reserva_autoriza_el_pago() throws Exception {
        when(paymentPort.process(any())).thenReturn(
            new PaymentResult(UUID.randomUUID(), BOOKING_ID, PaymentStatus.APPROVED, "ok"));

        mvc.perform(authorize("key-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bookingId").value(BOOKING_ID.toString()))
            .andExpect(jsonPath("$.status").value("APPROVED"));
        verify(paymentPort).process(any());
    }

    @Test
    @WithMockUser(username = "other@test.com", roles = "CUSTOMER")
    void otro_customer_recibe_403_y_no_se_invoca_la_pasarela() throws Exception {
        mvc.perform(authorize("key-2"))
            .andExpect(status().isForbidden());

        verify(paymentPort, never()).process(any());
        verify(paymentResults, never()).save(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_no_paga_por_terceros() throws Exception {
        mvc.perform(authorize("key-3"))
            .andExpect(status().isForbidden());
        verify(paymentPort, never()).process(any());
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_no_paga_por_terceros() throws Exception {
        mvc.perform(authorize("key-4"))
            .andExpect(status().isForbidden());
        verify(paymentPort, never()).process(any());
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "CUSTOMER")
    void reserva_inexistente_responde_404() throws Exception {
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.empty());

        mvc.perform(authorize("key-5"))
            .andExpect(status().isNotFound());
        verify(paymentPort, never()).process(any());
    }

    @Test
    @WithMockUser(username = "other@test.com", roles = "CUSTOMER")
    void replay_de_idempotency_key_ajena_responde_403_sin_devolver_el_resultado_guardado() throws Exception {
        // El dueño ya pagó con "key-6"; otro usuario intenta reutilizar esa clave sobre la reserva del dueño
        PaymentResult stored = new PaymentResult(UUID.randomUUID(), BOOKING_ID, PaymentStatus.APPROVED, "secreto-guardado");
        when(paymentResults.findByIdempotencyKey("key-6")).thenReturn(Optional.of(stored));

        mvc.perform(authorize("key-6"))
            .andExpect(status().isForbidden())
            .andExpect(content().string(not(containsString("secreto-guardado"))));
        verify(paymentPort, never()).process(any());
    }

    @Test
    @WithMockUser(username = "other@test.com", roles = "CUSTOMER")
    void replay_de_idempotency_key_ajena_sobre_reserva_propia_responde_403() throws Exception {
        // other es dueño de su propia reserva, pero "key-7" ya está asociada al pago de otra reserva
        UUID ownBooking = UUID.randomUUID();
        when(bookings.findById(ownBooking)).thenReturn(Optional.of(bookingOf(OTHER_ID)));
        PaymentResult stored = new PaymentResult(UUID.randomUUID(), BOOKING_ID, PaymentStatus.APPROVED, "secreto-guardado");
        when(paymentResults.findByIdempotencyKey("key-7")).thenReturn(Optional.of(stored));

        mvc.perform(authorize("key-7", ownBooking))
            .andExpect(status().isForbidden())
            .andExpect(content().string(not(containsString("secreto-guardado"))));
        verify(paymentPort, never()).process(any());
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = "CUSTOMER")
    void dueno_puede_repetir_su_propia_idempotency_key() throws Exception {
        PaymentResult stored = new PaymentResult(UUID.randomUUID(), BOOKING_ID, PaymentStatus.APPROVED, "ok-previo");
        when(paymentResults.findByIdempotencyKey("key-8")).thenReturn(Optional.of(stored));

        mvc.perform(authorize("key-8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("ok-previo"));
        verify(paymentPort, never()).process(any());
    }

    @Test
    void sin_autenticacion_responde_401() throws Exception {
        mvc.perform(authorize("key-9"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentPort, bookings);
    }

    private MockHttpServletRequestBuilder authorize(String key) {
        return authorize(key, BOOKING_ID);
    }

    private MockHttpServletRequestBuilder authorize(String key, UUID bookingId) {
        return post("/api/payments/authorize").with(csrf())
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bookingId\":\"" + bookingId + "\",\"amount\":50.00,\"cardNumber\":\"1111\","
                + "\"expiryDate\":\"12/30\",\"cvv\":\"123\"}");
    }

    private Booking bookingOf(UUID customerId) {
        return new Booking(BOOKING_ID, UUID.randomUUID(), customerId,
            Instant.parse("2030-01-01T12:00:00Z"), BookingStatus.CREATED, 0);
    }
}
