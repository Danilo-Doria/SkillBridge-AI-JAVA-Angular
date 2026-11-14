package com.riwi.skillbridge.ai.infrastructure.config;

import com.riwi.skillbridge.ai.infrastructure.security.CsrfCookieFilter;
import com.riwi.skillbridge.ai.infrastructure.security.JwtAuthenticationFilter;
import com.riwi.skillbridge.ai.infrastructure.security.RestAccessDeniedHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Misma política que {@code SecurityConfiguration} del monolito (feature/104): sesión stateless, JWT por cookie o
 * Bearer, CSRF con {@link CookieCsrfTokenRepository} solo para peticiones mutantes autenticadas por cookie,
 * 401 sin credenciales, 403 ProblemDetail sin permisos. ai-service no expone login ni emite tokens.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /**
     * SameSite de la cookie XSRF-TOKEN configurable (HU-104, constitución art. III: Strict en producción con origen
     * único detrás del gateway). En desarrollo el defecto None permite el frontend cross-origin (:4200).
     */
    @Value("${app.csrf.cookie.same-site:None}")
    private String csrfCookieSameSite;

    private CookieCsrfTokenRepository cookieCsrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(customizer -> customizer.sameSite(csrfCookieSameSite).secure(true));
        return repo;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http,
                                 JwtAuthenticationFilter jwtFilter,
                                 RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        // Peticiones con Bearer no usan cookie, así que no son vulnerables a CSRF
        RequestMatcher bearerRequests = request -> {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            return header != null && header.startsWith("Bearer ");
        };
        RequestMatcher cookieAuthenticatedUnsafeRequests = request -> {
            if (Set.of("GET", "HEAD", "TRACE", "OPTIONS").contains(request.getMethod())
                || bearerRequests.matches(request)) {
                return false;
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        };

        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(cookieCsrfTokenRepository())
                .csrfTokenRequestHandler(csrfHandler)
                .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                .requireCsrfProtectionMatcher(cookieAuthenticatedUnsafeRequests)
                .ignoringRequestMatchers(bearerRequests))
            .cors(cors -> {
            })
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**",
                    "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, CsrfFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .build();
    }

    /** El filtro JWT solo debe ejecutarse dentro de la cadena de seguridad, no también como filtro de servlet. */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Evita que Spring Boot genere un usuario/contraseña por defecto: ai-service no autentica con credenciales. */
    @Bean
    UserDetailsService noUsers() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-XSRF-TOKEN", "X-Correlation-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
