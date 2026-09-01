package com.wydad.digital.communication.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * B.18/C.21 — Client minimal vers auth-service pour lire le rôle et la
 * discipline d'un utilisateur. Utilisé par {@code MessagingService} pour
 * décider si un joueur peut écrire au président de SA discipline.
 *
 * <p>Best-effort : en cas d'erreur réseau, on retourne null (refus par
 * défaut, fail-closed).</p>
 */
@Slf4j
@Component
public class AuthUserInfoClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRoleDiscipline(Long id, String email, String role, String discipline) {}

    public AuthUserInfoClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/auth/internal";
        this.internalSecret = internalSecret;
    }

    public UserRoleDiscipline getRoleAndDiscipline(Long userId) {
        if (userId == null) return null;
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        try {
            ResponseEntity<UserRoleDiscipline> response = restTemplate.exchange(
                    baseUrl + "/users/" + userId + "/discipline",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserRoleDiscipline.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("auth-service injoignable pour getRoleAndDiscipline({}) : {}",
                    userId, e.getMessage());
            return null;
        }
    }
}
