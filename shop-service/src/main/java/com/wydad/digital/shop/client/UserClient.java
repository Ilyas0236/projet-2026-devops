package com.wydad.digital.shop.client;

import com.wydad.digital.shop.dto.UserFullNameDto;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Client HTTP vers auth-service pour récupérer les informations utilisateurs
 * (fullName, etc.) nécessaires aux listings admin.
 *
 * <p>Best-effort : si auth-service est indisponible, le mapper retourne
 * simplement l'email à la place du fullName (fallback transparent côté front).</p>
 *
 * <p>Auth : le contrôleur appelant doit être dans un contexte authentifié
 * ADMIN — les headers X-User-Email / X-User-Role sont ajoutés par la
 * gateway en amont, on les transmet tels quels via {@link HttpHeaders}.</p>
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
     * Récupère tous les utilisateurs (ADMIN only) puis indexe par email.
     * 1 seul appel HTTP — pas de N+1.
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

    /** Lookup ponctuel d'un fullName (utilisé à la création de commande). */
    public String getFullNameFor(String email) {
        if (email == null || email.isBlank()) return null;
        return getAllFullNamesByEmail().getOrDefault(email, email);
    }
}
