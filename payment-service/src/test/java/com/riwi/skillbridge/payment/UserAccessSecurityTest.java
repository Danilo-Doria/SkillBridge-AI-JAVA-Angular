package com.riwi.skillbridge.payment;

import com.riwi.skillbridge.payment.application.port.in.SyncUserAccessUseCase;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.domain.model.AccessStatus;
import com.riwi.skillbridge.payment.domain.model.UserAccess;
import com.riwi.skillbridge.payment.support.TestInfrastructure;
import com.riwi.skillbridge.payment.support.TestJwt;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revocación de JWT válidos (HU-104, hallazgo S1) con el estado de {@code payment.user_access}. Un 409
 * ({@code BOOKING_NOT_SYNCHRONIZED}) significa que la petición pasó la seguridad (no existe la reserva).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAccessSecurityTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry, true);
    }

    @Autowired MockMvc mvc;
    @Autowired SyncUserAccessUseCase sync;
    @MockitoBean PaymentGatewayPort gateway;

    @BeforeEach
    void gateway() {
        when(gateway.isConfigured()).thenReturn(true);
    }

    private ResultActions authorizeWithBearer(String token) throws Exception {
        return mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":\"" + UUID.randomUUID() + "\",\"amount\":50.00}"));
    }

    @Test
    void userWithoutAccessRowIsAuthenticatedAsBefore() throws Exception {
        authorizeWithBearer(TestJwt.token(UUID.randomUUID(), "new@test.local", "CUSTOMER"))
                .andExpect(status().isConflict());
    }

    @Test
    void suspendedUserWithValidTokenIs401() throws Exception {
        UUID userId = UUID.randomUUID();
        sync.apply(new UserAccess(userId, AccessStatus.SUSPENDED, "CUSTOMER"), UUID.randomUUID(), Instant.now());
        String token = TestJwt.token(userId, "s@test.local", "CUSTOMER");

        authorizeWithBearer(token).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/authorize").header("Idempotency-Key", "cookie-1")
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test
    void changedRoleIs401UntilTheUserLogsInAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        sync.apply(new UserAccess(userId, AccessStatus.ACTIVE, "PROVIDER"), UUID.randomUUID(), Instant.now());

        authorizeWithBearer(TestJwt.token(userId, "r@test.local", "CUSTOMER")).andExpect(status().isUnauthorized());
        // Token nuevo con el rol actual: vuelve a autenticar.
        authorizeWithBearer(TestJwt.token(userId, "r@test.local", "PROVIDER")).andExpect(status().isConflict());
    }

    @Test
    void reactivatedUserWorksAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwt.token(userId, "back@test.local", "CUSTOMER");
        Instant suspendedAt = Instant.now().minusSeconds(10);
        sync.apply(new UserAccess(userId, AccessStatus.SUSPENDED, "CUSTOMER"), UUID.randomUUID(), suspendedAt);
        authorizeWithBearer(token).andExpect(status().isUnauthorized());

        sync.apply(new UserAccess(userId, AccessStatus.ACTIVE, "CUSTOMER"), UUID.randomUUID(), suspendedAt.plusSeconds(5));
        authorizeWithBearer(token).andExpect(status().isConflict());
    }
}
