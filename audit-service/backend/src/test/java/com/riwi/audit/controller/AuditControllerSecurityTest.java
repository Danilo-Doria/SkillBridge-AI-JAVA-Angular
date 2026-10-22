package com.riwi.audit.controller;

import com.riwi.audit.config.CorsConfig;
import com.riwi.audit.config.SecurityConfig;
import com.riwi.audit.model.BusinessEvent;
import com.riwi.audit.repository.AuditEventRepository;
import com.riwi.audit.security.JwtTokenVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-004: /api/audit/** exige un JWT HS256 de ADMIN firmado con el secreto compartido con el backend.
 * Los tokens se generan con el mismo esquema que {@code JwtService} del backend (claim "role").
 */
@WebMvcTest(
    controllers = {AuditControllerSecurityTest.HealthStub.class, AuditController.class},
    properties = "app.jwt.secret=" + AuditControllerSecurityTest.SECRET)
@Import({SecurityConfig.class, JwtTokenVerifier.class, CorsConfig.class, AuditControllerSecurityTest.HealthStub.class})
class AuditControllerSecurityTest {

    static final String SECRET = "test-only-secret-with-at-least-32-chars!!";
    private static final String OTHER_SECRET = "another-test-only-secret-with-32-chars!!";
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Autowired MockMvc mvc;

    @MockitoBean AuditEventRepository repository;

    /** WebMvcTest no autoconfigura actuator: un stub en la misma ruta demuestra que la regla es pública. */
    @RestController
    static class HealthStub {
        @GetMapping("/actuator/health/liveness")
        Map<String, String> liveness() {
            return Map.of("status", "UP");
        }
    }

    @BeforeEach
    void setUp() {
        BusinessEvent event = BusinessEvent.builder()
            .eventId(EVENT_ID).eventType("BookingCreated").aggregateId("agg-1").aggregateType("Booking")
            .occurredAt(Instant.now()).correlationId("corr-1").version(1).payload(Map.of("k", "v")).build();
        when(repository.findAllByOrderByOccurredAtDesc()).thenReturn(List.of(event));
        when(repository.findAll()).thenReturn(List.of(event));
        when(repository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    // --- acceso permitido -------------------------------------------------------------------

    @Test
    void admin_lista_eventos() throws Exception {
        mvc.perform(bearer(get("/api/audit/events"), token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventId").value(EVENT_ID.toString()));
    }

    @Test
    void admin_consulta_un_evento() throws Exception {
        mvc.perform(bearer(get("/api/audit/events/" + EVENT_ID), token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventType").value("BookingCreated"));
    }

    @Test
    void admin_consulta_estadisticas() throws Exception {
        mvc.perform(bearer(get("/api/audit/stats"), token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalEvents").value(1))
            .andExpect(jsonPath("$.bookingCreated").value(1));
    }

    @Test
    void health_es_publico() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void preflight_cors_con_header_authorization_sigue_permitido() throws Exception {
        mvc.perform(options("/api/audit/events")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    // --- acceso denegado --------------------------------------------------------------------

    @Test
    void sin_token_responde_401_en_los_tres_endpoints() throws Exception {
        for (String path : List.of("/api/audit/events", "/api/audit/events/" + EVENT_ID, "/api/audit/stats")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void customer_y_provider_reciben_403_en_los_tres_endpoints() throws Exception {
        for (String role : List.of("CUSTOMER", "PROVIDER")) {
            for (String path : List.of("/api/audit/events", "/api/audit/events/" + EVENT_ID, "/api/audit/stats")) {
                mvc.perform(bearer(get(path), token(role))).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rol_desconocido_o_ausente_recibe_403() throws Exception {
        mvc.perform(bearer(get("/api/audit/events"), token("SUPERUSER"))).andExpect(status().isForbidden());
        mvc.perform(bearer(get("/api/audit/events"),
                Jwts.builder().subject("x@test.com").expiration(inMinutes(5)).signWith(key(SECRET)).compact()))
            .andExpect(status().isForbidden());
    }

    @Test
    void token_expirado_responde_401() throws Exception {
        String expired = Jwts.builder().subject("admin@test.com").claim("role", "ADMIN")
            .issuedAt(inMinutes(-20)).expiration(inMinutes(-10)).signWith(key(SECRET)).compact();

        mvc.perform(bearer(get("/api/audit/events"), expired)).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void token_sin_expiracion_responde_401() throws Exception {
        String eternal = Jwts.builder().subject("admin@test.com").claim("role", "ADMIN")
            .signWith(key(SECRET)).compact();

        mvc.perform(bearer(get("/api/audit/events"), eternal)).andExpect(status().isUnauthorized());
    }

    @Test
    void firma_invalida_responde_401() throws Exception {
        String forged = Jwts.builder().subject("admin@test.com").claim("role", "ADMIN")
            .expiration(inMinutes(5)).signWith(key(OTHER_SECRET)).compact();

        mvc.perform(bearer(get("/api/audit/events"), forged)).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void token_sin_firma_alg_none_responde_401() throws Exception {
        String unsigned = Jwts.builder().subject("admin@test.com").claim("role", "ADMIN")
            .expiration(inMinutes(5)).compact();

        mvc.perform(bearer(get("/api/audit/events"), unsigned)).andExpect(status().isUnauthorized());
    }

    @Test
    void token_malformado_o_esquema_distinto_de_bearer_responde_401() throws Exception {
        mvc.perform(bearer(get("/api/audit/events"), "no-es-un-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/audit/events").header("Authorization", "Basic YWRtaW46YWRtaW4="))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rutas_no_listadas_tambien_exigen_autenticacion() throws Exception {
        mvc.perform(get("/internal/anything")).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static String token(String role) {
        return Jwts.builder().subject(role.toLowerCase() + "@test.com").claim("role", role)
            .issuedAt(new Date()).expiration(inMinutes(5)).signWith(key(SECRET)).compact();
    }

    private static Date inMinutes(long minutes) {
        return Date.from(Instant.now().plusSeconds(minutes * 60));
    }

    private static javax.crypto.SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
