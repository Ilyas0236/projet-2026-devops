package com.wydad.digital.sports.config;

import com.wydad.digital.sports.filter.UserContextFilter;
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
                .authorizeHttpRequests(auth -> auth
                        // La route roster interne est protégée par le secret partagé
                        // X-Internal-Secret validé DANS InternalRosterController (pas
                        // par Spring Security) : un appel service-à-service n'a pas
                        // d'en-têtes X-User-* et serait sinon rejeté à tort en 403.
                        // La gateway bloque de toute façon /api/sports/internal/**
                        // depuis l'extérieur.
                        .requestMatchers("/api/sports/internal/**").permitAll()
                        // §9 — la liste des convocations PUBLIÉES apparaît
                        // automatiquement sur le site public : lecture anonyme,
                        // le service ne renvoie de toute façon que du PUBLIEE.
                        .requestMatchers("/api/sports/match-convocations/public/**").permitAll()
                        // Toute la surface HTTP exige une identité propagée par la
                        // gateway. Les sondages (→ election-service) et le chat WS
                        // (→ communication-service) ont quitté ce service.
                        .anyRequest().authenticated())
                .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
