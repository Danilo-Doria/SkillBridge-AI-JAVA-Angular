package com.riwi.audit.config;

import com.riwi.audit.security.BearerJwtAuthenticationFilter;
import com.riwi.audit.security.JwtTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenVerifier verifier) throws Exception {
        return http
            // Solo se acepta Authorization: Bearer; el servicio no lee cookies ni crea sesión, así que no hay
            // credencial ambiental que un sitio ajeno pueda reutilizar y la protección CSRF no aporta nada.
            .csrf(AbstractHttpConfigurer::disable)
            // Reutiliza la configuración CORS MVC existente (CorsConfig); el preflight se resuelve antes de autenticar
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/audit/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(new BearerJwtAuthenticationFilter(verifier), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
