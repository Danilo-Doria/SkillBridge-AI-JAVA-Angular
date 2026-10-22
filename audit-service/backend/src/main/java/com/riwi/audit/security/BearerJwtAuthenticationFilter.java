package com.riwi.audit.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Autentica peticiones con {@code Authorization: Bearer <jwt>}. Un token ausente, inválido o expirado deja
 * la petición sin autenticar (el entry point responde 401); nunca se registra el token.
 */
public class BearerJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> KNOWN_ROLES = Set.of("CUSTOMER", "PROVIDER", "ADMIN");

    private final JwtTokenVerifier verifier;

    public BearerJwtAuthenticationFilter(JwtTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            verifier.verify(header.substring(BEARER_PREFIX.length()).trim()).ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        Object role = claims.get("role");
        List<SimpleGrantedAuthority> authorities = role instanceof String r && KNOWN_ROLES.contains(r)
            ? List.of(new SimpleGrantedAuthority("ROLE_" + r))
            : List.of();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(claims.getSubject(), null, authorities));
    }
}
