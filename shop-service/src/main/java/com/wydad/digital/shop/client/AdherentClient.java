package com.wydad.digital.shop.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Vérifie si un email correspond à un ADHÉRENT (abonnement saisonnier actif).
 * Appelle l'endpoint interne auth-service /api/auth/subscriptions/internal/is-adherent.
 *
 * Best-effort : si auth-service est indisponible, on traite l'utilisateur comme
 * SUPPORTER (pas de réduction) plutôt que de bloquer un achat.
 */
@Slf4j
@Component
public class AdherentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public AdherentClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl;
        this.internalSecret = internalSecret;
    }

    /**
     * @return true si l'utilisateur a un abonnement saisonnier ACTIF non expiré.
     *         false sinon (ou en cas d'erreur réseau, logguée en warn).
     */
    public boolean isActiveAdherent(String email) {
        if (email == null || email.isBlank()) return false;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            ResponseEntity<Boolean> resp = restTemplate.getForEntity(
                    baseUrl + "/api/auth/subscriptions/internal/is-adherent?email=" + email,
                    Boolean.class, headers);
            Boolean body = resp.getBody();
            return body != null && body;
        } catch (RestClientException e) {
            log.warn("Adherent check indisponible pour {}: {}", email, e.getMessage());
            return false;
        }
    }
}
