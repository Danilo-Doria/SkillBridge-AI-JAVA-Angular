package com.riwi.skillbridge.ai.infrastructure.security;

import com.riwi.skillbridge.ai.support.TestJwt;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidationServiceTest {

    private final JwtValidationService service = new JwtValidationService(TestJwt.PUBLIC_PEM, TestJwt.KEY_ID);

    @Test
    void acceptsMonolithShapedRs256Token() {
        UUID uid = UUID.randomUUID();
        AuthenticatedUser user = service.validate(TestJwt.token(uid, "ana@test.local", "PROVIDER"));
        assertThat(user.id()).isEqualTo(uid);
        assertThat(user.email()).isEqualTo("ana@test.local");
        assertThat(user.getName()).isEqualTo("ana@test.local");
        assertThat(user.role()).isEqualTo("PROVIDER");
    }

    @Test
    void acceptsEscapedNewlinesInPem() {
        JwtValidationService escaped = new JwtValidationService(TestJwt.PUBLIC_PEM.replace("\n", "\\n"), TestJwt.KEY_ID);
        assertThat(escaped.validate(TestJwt.customer()).role()).isEqualTo("CUSTOMER");
    }

    @Test
    void rejectsWrongKidWrongIssuerAndHs256() {
        Instant exp = Instant.now().plus(5, ChronoUnit.MINUTES);
        assertThatThrownBy(() -> service.validate(TestJwt.builder(UUID.randomUUID(), "a@b.c", "CUSTOMER", "k2", "skillbridge", exp)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.validate(TestJwt.builder(UUID.randomUUID(), "a@b.c", "CUSTOMER", TestJwt.KEY_ID, "evil", exp)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.validate(TestJwt.legacyHs256())).isInstanceOf(JwtException.class);
    }

    @Test
    void failsFastWithoutPublicKey() {
        assertThatThrownBy(() -> new JwtValidationService("", TestJwt.KEY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY");
    }
}
