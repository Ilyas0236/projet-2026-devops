package com.wydad.digital.communication.config;

import com.wydad.digital.communication.filter.UserContextFilter;
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
                        // Handshake SockJS/STOMP du chat de groupe : un upgrade
                        // WebSocket ne peut pas porter de header Authorization,
                        // le JWT est validé à la frame CONNECT par l'interceptor
                        // STOMP (TeamChatAuthInterceptor). La gateway laisse déjà
                        // passer /ws/team-chat* sans JWT (Phase 4).
                        .requestMatchers("/ws/team-chat", "/ws/team-chat/**").permitAll()
                        // Toute la messagerie est personnelle : aucune route HTTP
                        // publique. L'identité vient des en-têtes X-User-* posés par
                        // la gateway depuis un JWT validé.
                        .anyRequest().authenticated())
                .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
