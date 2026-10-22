package com.riwi.audit.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El secreto JWT es obligatorio: sin él el arranque debe fallar de forma explícita (sin default inseguro). */
class JwtTokenVerifierTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(JwtTokenVerifier.class);

    @Test
    void arranque_falla_si_falta_app_jwt_secret() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.jwt.secret");
        });
    }

    @Test
    void arranque_falla_si_el_secreto_esta_en_blanco() {
        runner.withPropertyValues("app.jwt.secret=   ").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_SECRET");
        });
    }

    @Test
    void arranque_falla_si_el_secreto_tiene_menos_de_32_caracteres() {
        runner.withPropertyValues("app.jwt.secret=corto").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("32");
        });
    }

    @Test
    void arranca_con_un_secreto_valido() {
        runner.withPropertyValues("app.jwt.secret=test-only-secret-with-at-least-32-chars!!")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void constructor_rechaza_secretos_invalidos() {
        assertThatThrownBy(() -> new JwtTokenVerifier(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenVerifier("")).isInstanceOf(IllegalStateException.class);
    }
}
