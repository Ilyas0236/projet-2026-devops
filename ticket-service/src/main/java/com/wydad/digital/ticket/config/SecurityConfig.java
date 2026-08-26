package com.wydad.digital.ticket.config;

import com.wydad.digital.ticket.filter.UserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import jakarta.servlet.DispatcherType;
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
                        // Dispatch d erreur Spring : le laisser passer pour renvoyer le vrai code
                        // (400/500) ; sinon /error est re-securise et renvoie 403 qui masque la cause.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Catalogue d'événements : donnée publique consultable
                        // par un visiteur anonyme (page /billetterie du site
                        // public). Achat et gestion des billets restent réservés.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/ticket/events", "/api/ticket/events/{id}",
                                "/api/ticket/events/upcoming",
                                "/api/ticket/events/type/{type}").permitAll()
                        // Routes internes service-à-service : protégées par le
                        // secret partagé X-Internal-Secret (validé dans le
                        // contrôleur) ; la gateway les bloque en amont.
                        .requestMatchers("/api/ticket/internal/**").permitAll()
                        // La recherche sert aussi à l'achat (membre) : conservée AUTH.
                        .anyRequest().authenticated())
                .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
