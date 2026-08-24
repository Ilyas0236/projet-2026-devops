package com.wydad.digital.notification.config;

import com.wydad.digital.notification.filter.UserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserContextFilter userContextFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Les routes /internal/** sont protégées par le secret partagé
                // X-Internal-Secret validé DANS le contrôleur (pas par Spring
                // Security) : les appels service-à-service n'ont pas d'en-têtes
                // X-User-* et seraient sinon rejetés à tort en 401/403.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/notification/internal/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
