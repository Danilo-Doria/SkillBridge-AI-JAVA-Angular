package com.riwi.skillbridge.ai;

import com.riwi.skillbridge.ai.application.port.in.SyncUserAccessUseCase;
import com.riwi.skillbridge.ai.domain.model.AccessStatus;
import com.riwi.skillbridge.ai.domain.model.UserAccess;
import com.riwi.skillbridge.ai.support.AbstractIntegrationTest;
import com.riwi.skillbridge.ai.support.TestJwt;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Revocación de JWT válidos (HU-104, hallazgo S1) con el estado de {@code ai.user_access}. */
@SpringBootTest
@AutoConfigureMockMvc
class UserAccessSecurityTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SyncUserAccessUseCase sync;

    private ResultActions providers(String token) throws Exception {
        return mvc.perform(get("/api/admin/ai/providers").header("Authorization", "Bearer " + token));
    }

    @Test
    void adminWithoutAccessRowIsAuthorizedAsBefore() throws Exception {
        providers(TestJwt.token(UUID.randomUUID(), "admin@test.local", "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void suspendedUserWithValidTokenIs401() throws Exception {
        UUID userId = UUID.randomUUID();
        sync.apply(new UserAccess(userId, AccessStatus.SUSPENDED, "ADMIN"), UUID.randomUUID(), Instant.now());
        String token = TestJwt.token(userId, "admin@test.local", "ADMIN");

        providers(token).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ai/providers").cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changedRoleIs401UntilTheUserLogsInAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        sync.apply(new UserAccess(userId, AccessStatus.ACTIVE, "CUSTOMER"), UUID.randomUUID(), Instant.now());

        // Token antiguo con ROLE_ADMIN tras degradarlo a CUSTOMER: 401, no 200.
        providers(TestJwt.token(userId, "demoted@test.local", "ADMIN")).andExpect(status().isUnauthorized());
        // Token nuevo con el rol actual: autentica (403 por falta de ROLE_ADMIN, ya no 401).
        providers(TestJwt.token(userId, "demoted@test.local", "CUSTOMER")).andExpect(status().isForbidden());
    }

    @Test
    void reactivatedUserWorksAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwt.token(userId, "back@test.local", "ADMIN");
        Instant suspendedAt = Instant.now().minusSeconds(10);
        sync.apply(new UserAccess(userId, AccessStatus.SUSPENDED, "ADMIN"), UUID.randomUUID(), suspendedAt);
        providers(token).andExpect(status().isUnauthorized());

        sync.apply(new UserAccess(userId, AccessStatus.ACTIVE, "ADMIN"), UUID.randomUUID(), suspendedAt.plusSeconds(5));
        providers(token).andExpect(status().isOk());
    }
}
