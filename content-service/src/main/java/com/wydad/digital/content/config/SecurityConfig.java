package com.wydad.digital.content.config;

import com.wydad.digital.content.filter.UserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        // B.10 : les réclamations ne sont JAMAIS publiques —
                        // règle placée AVANT le permitAll général des GET vitrine
                        .requestMatchers(HttpMethod.GET,
                                "/api/content/reclamations",
                                "/api/content/reclamations/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/content/**").permitAll()
                        // Endpoints internes service-à-service : authentifiés par le
                        // secret partagé X-Internal-Secret au niveau contrôleur
                        .requestMatchers("/api/content/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
