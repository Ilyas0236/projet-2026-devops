package com.wydad.digital.payment.client;

import com.wydad.digital.payment.dto.UserFullNameDto;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Client HTTP vers auth-service pour récupérer les informations utilisateurs
 * (fullName) nécessaires au listing admin des dons. 1 seul appel HTTP pour
 * éviter le N+1.
 *
 * <p>Best-effort : si auth-service est indisponible, on retourne une map
 * vide et le mapper retombe sur l'email (fallback transparent côté front).</p>
 */
@Slf4j
@Component
public class UserClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public UserClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl;
        this.internalSecret = internalSecret;
    }

    /**
     * Récupère tous les utilisateurs (ADMIN only côté auth-service) puis
     * indexe par email. Utilisé pour enrichir le listing admin des dons.
     */
    public Map<String, String> getAllFullNamesByEmail() {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            ResponseEntity<List<UserFullNameDto>> resp = restTemplate.exchange(
                    baseUrl + "/api/auth/admin/users",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<UserFullNameDto>>() {}
            );
            List<UserFullNameDto> body = resp.getBody();
            if (body == null) return Collections.emptyMap();
            return body.stream().filter(u -> u.email() != null)
                    .collect(Collectors.toMap(
                            UserFullNameDto::email,
                            u -> {
                                String fn = u.firstName() != null ? u.firstName() : "";
                                String ln = u.lastName() != null ? u.lastName() : "";
                                String full = (fn + " " + ln).trim();
                                return full.isEmpty() ? u.email() : full;
                            },
                            (a, b) -> a,
                            java.util.HashMap::new));
        } catch (RestClientException e) {
            log.warn("UserClient indisponible: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
