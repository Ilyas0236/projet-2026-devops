package com.wydad.digital.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client interne vers auth-service pour récupérer les destinataires actifs
 * (broadcast). Protégé par le secret partagé X-Internal-Secret.
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public AuthServiceClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/auth";
        this.internalSecret = internalSecret;
    }

    /**
     * Retourne la liste des utilisateurs actifs (id, email, ...).
     * Liste vide si auth-service est indisponible : un broadcast ne doit
     * jamais bloquer le service de notifications.
     */
    public List<Recipient> fetchActiveRecipients() {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    baseUrl + "/internal/recipients",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() == null ? List.of() : response.getBody().stream()
                    .map(m -> new Recipient(
                            ((Number) m.get("id")).longValue(),
                            (String) m.get("email")))
                    .toList();
        } catch (RestClientException e) {
            log.warn("Impossible de recuperer les destinataires actifs : {}", e.getMessage());
            return List.of();
        }
    }

    public record Recipient(Long userId, String email) {}
}
