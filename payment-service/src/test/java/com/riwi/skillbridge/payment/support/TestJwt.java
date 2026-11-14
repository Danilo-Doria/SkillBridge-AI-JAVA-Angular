package com.riwi.skillbridge.payment.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Emite tokens con la misma forma que {@code JwtService.generate} del monolito (feature/104): RS256, kid,
 * iss=skillbridge, sub=email, claims uid/email/role. Solo para tests.
 */
public final class TestJwt {
    public static final String KEY_ID = "skillbridge-1";
    public static final KeyPair KEYS = generate();
    public static final String PUBLIC_PEM = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(KEYS.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----\n";

    private TestJwt() {
    }

    public static String token(UUID uid, String email, String role) {
        return builder(uid, email, role, KEY_ID, "skillbridge", Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    public static String customer() {
        return token(UUID.randomUUID(), "customer@test.local", "CUSTOMER");
    }

    public static String admin() {
        return token(UUID.randomUUID(), "admin@test.local", "ADMIN");
    }

    public static String builder(UUID uid, String email, String role, String kid, String issuer, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(kid).and()
                .issuer(issuer)
                .subject(email)
                .claim("uid", uid.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now.minus(1, ChronoUnit.MINUTES)))
                .expiration(Date.from(expiresAt))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** Token HS256 heredado: payment-service debe rechazarlo. */
    public static String legacyHs256() {
        return Jwts.builder()
                .subject("legacy@test.local")
                .claim("uid", UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
