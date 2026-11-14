package com.riwi.skillbridge.ai.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Validación (nunca emisión) de los JWT RS256 que emite el monolito ({@code JwtService} de feature/104).
 *
 * <p>ai-service solo conoce la clave pública ({@code JWT_PUBLIC_KEY}, PEM X.509). Sin clave pública la aplicación
 * no arranca. Reglas: algoritmo RS256 únicamente (HS256 heredado no se acepta), emisor {@code skillbridge},
 * expiración vigente (jjwt) y, si el token trae {@code kid}, debe coincidir con {@code app.jwt.key-id}.
 */
@Service
public class JwtValidationService {
    static final String ISSUER = "skillbridge";
    private static final String RS256 = "RS256";
    private static final int MIN_RSA_BITS = 2048;

    private final PublicKey publicKey;
    private final String keyId;

    public JwtValidationService(@Value("${app.jwt.public-key:}") String publicPem,
                                @Value("${app.jwt.key-id:skillbridge-1}") String keyId) {
        this.publicKey = parsePublicKey(publicPem);
        this.keyId = keyId;
    }

    /** Valida firma, algoritmo, kid, emisor y expiración y devuelve el usuario autenticado. */
    public AuthenticatedUser validate(String token) {
        Jws<Claims> jws = Jwts.parser()
            .keyLocator(new LocatorAdapter<Key>() {
                @Override
                protected Key locate(JwsHeader header) {
                    if (!RS256.equals(header.getAlgorithm())) {
                        throw new JwtException("Unsupported JWT algorithm");
                    }
                    String kid = header.getKeyId();
                    if (kid != null && keyId != null && !keyId.isBlank() && !keyId.equals(kid)) {
                        throw new JwtException("Unknown JWT key id");
                    }
                    return publicKey;
                }
            })
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token);
        Claims claims = jws.getPayload();
        String uid = claims.get("uid", String.class);
        String role = claims.get("role", String.class);
        String email = claims.getSubject();
        if (uid == null || role == null || email == null || email.isBlank()) {
            throw new JwtException("JWT is missing required claims (uid, role, sub)");
        }
        return new AuthenticatedUser(UUID.fromString(uid), email, role);
    }

    private static PublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("app.jwt.public-key (JWT_PUBLIC_KEY) is required: ai-service only validates "
                + "RS256 tokens issued by the monolith");
        }
        try {
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodePem(pem)));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalArgumentException("JWT public key must be RSA >= " + MIN_RSA_BITS + " bits");
            }
            return key;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid JWT public key (expected X.509 PEM)", e);
        }
    }

    /** Acepta PEM con saltos reales o con secuencias literales {@code \n} típicas de variables de entorno. */
    private static byte[] decodePem(String pem) {
        String body = pem.replace("\\n", "\n")
            .replaceAll("-----BEGIN [A-Z ]+-----", "")
            .replaceAll("-----END [A-Z ]+-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
