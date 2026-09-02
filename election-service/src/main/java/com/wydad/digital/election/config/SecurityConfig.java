package com.wydad.digital.election.config;

import com.wydad.digital.election.filter.UserContextFilter;
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
                        // Résultats PUBLIÉS + scrutins EN COURS : donnée non personnelle
                        // consultable par un visiteur anonyme (site officiel, sans
                        // connexion). Le VOTE et l'administration restent authentifiés.
                        // B.8 — /api/elections/open expose la participation X/Y sans
                        // détail par candidat (transparence pendant le scrutin).
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/elections/published",
                                "/api/elections/published/latest",
                                "/api/elections/open",
                                // Sondages actifs : lecture publique (page /sondages
                                // du site officiel, visiteur non connecté inclus).
                                // Le VOTE et l'administration restent authentifiés.
                                "/api/polls/active").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
