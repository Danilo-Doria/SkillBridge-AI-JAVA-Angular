package com.riwi.skillbridge.ai.infrastructure.security;

import com.riwi.skillbridge.ai.application.port.in.CheckUserAccessUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Igual que el filtro del monolito (cookie {@code access_token} primero, {@code Bearer} como respaldo), pero sin
 * consultar usuarios en BD: la identidad sale de las claims del token validado con la clave pública.
 *
 * <p>Revocación (HU-104, hallazgo S1): tras validar la firma se consulta por PK el estado local del usuario
 * ({@code user_access}, alimentado por {@code UserAccessChanged}). Si está {@code SUSPENDED} o su rol actual difiere del
 * claim {@code role}, la petición no se autentica (401, igual que un token inválido; el usuario debe volver a iniciar
 * sesión). Sin fila: usuario activo con el rol del token. Si la consulta falla, también se deniega (fail-closed).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtValidationService jwt;
    private final CheckUserAccessUseCase userAccess;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtValidationService jwt,
                                   CheckUserAccessUseCase userAccess,
                                   @Value("${app.jwt.cookie.name:access_token}") String cookieName) {
        this.jwt = jwt;
        this.userAccess = userAccess;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedUser user = jwt.validate(token);
                if (!userAccess.isAllowed(user.id(), user.role())) {
                    log.info("JWT rejected: access revoked or role changed for userId={}", user.id());
                    throw new IllegalStateException("User access revoked");
                }
                var auth = new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            var fromCookie = Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
            if (fromCookie.isPresent()) {
                return fromCookie.get();
            }
        }
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }
}
