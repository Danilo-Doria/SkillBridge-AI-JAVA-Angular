package com.riwi.audit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Valida los JWT HS256 emitidos por el backend principal ({@code JwtService}) con el secreto compartido
 * {@code JWT_SECRET}. No importa clases de {@code backend/}: el contrato es solo el token
 * (subject = email, claim {@code role}, expiración obligatoria).
 */
@Component
public class JwtTokenVerifier {

    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey key;

    public JwtTokenVerifier(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "JWT_SECRET (app.jwt.secret) es obligatorio y debe tener al menos " + MIN_SECRET_LENGTH
                    + " caracteres; debe ser el mismo valor que usa el backend principal");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Devuelve los claims si el token está firmado con HS256, no ha expirado y declara expiración. */
    public Optional<Claims> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || claims.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
