package com.wydad.digital.sports.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Client interne vers auth-service pour résoudre les AUDIENCES des appels
 * programmés (Phase 5) : joueurs d'une catégorie (fiche Player), staff
 * d'une catégorie, adhérents PREMIUM. Même mécanisme que ticket-service
 * pour les billets VIP (endpoint /internal/recipients + X-Internal-Secret).
 */
@Slf4j
@Component
public class AuthClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String recipientsUrl;
    private final String internalSecret;

    /** Projection minimale de UserProfileResponse (auth-service). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserProfile(
            Long id,
            String email,
            String firstName,
            String lastName,
            String role,
            String membershipLevel,
            String statutCompte,
            boolean active) {

        public boolean isValide() {
            return "VALIDE".equals(statutCompte) && active;
        }
    }

    public AuthClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.recipientsUrl = baseUrl + "/api/auth/internal/recipients";
        this.internalSecret = internalSecret;
    }

    /** Retourne tous les utilisateurs actifs ; vide si auth injoignable (best-effort). */
    public List<UserProfile> getAllActiveUsers() {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            ResponseEntity<List<UserProfile>> response = restTemplate.exchange(
                    recipientsUrl, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<UserProfile>>() {});
            List<UserProfile> users = response.getBody() != null ? response.getBody() : Collections.emptyList();
            log.info("auth-service : {} utilisateur(s) actif(s) récupéré(s)", users.size());
            return users;
        } catch (Exception e) {
            log.error("auth-service injoignable ({}) - résolution d'audience impossible", recipientsUrl, e);
            return Collections.emptyList();
        }
    }
}
