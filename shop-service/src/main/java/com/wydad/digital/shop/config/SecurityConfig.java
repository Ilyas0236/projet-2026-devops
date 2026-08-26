package com.wydad.digital.shop.config;

import com.wydad.digital.shop.filter.UserContextFilter;
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
                        // Catalogue produits : donnée publique consultable par
                        // un visiteur anonyme (page /boutique du site public).
                        // Panier/commandes/promos restent strictement réservés.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/shop/products", "/api/shop/products/{id}").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
