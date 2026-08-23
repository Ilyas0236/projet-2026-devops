package com.wydad.digital.gamification.config;

import com.wydad.digital.gamification.filter.UserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserContextFilter userContextFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Le filtre d'identite (X-User-*) doit etre DANS la chaine Spring
            // Security pour que les roles soient pris en compte par @PreAuthorize
            .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Défense de titre : public (classement visible de tous)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/gamification/leaderboard").permitAll()
                // Endpoints internes service-à-service : authentifiés par le secret
                // partagé X-Internal-Secret au niveau contrôleur (jamais exposés
                // via la gateway)
                .requestMatchers("/api/gamification/internal/**").permitAll()
                // Tout le reste exige un utilisateur authentifié (la gateway a validé le JWT)
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
