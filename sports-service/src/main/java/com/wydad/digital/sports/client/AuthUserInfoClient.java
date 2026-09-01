package com.wydad.digital.sports.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * B.18/C.21 — Client service-à-service vers auth-service pour récupérer
 * le profil d'un utilisateur (rôle + discipline). Utilisé par
 * {@code TeamIsolationService.ensureCanQueryDiscipline(...)} : le président
 * ne peut consulter QUE sa propre discipline, et cette info vient du
 * serveur (pas falsifiable par le client).
 *
 * <p>Best-effort : en cas d'erreur réseau, on refuse l'appel (fail-closed).
 * Le check discipline est un garde-fou de sécurité, pas une optimisation.</p>
 */
@Slf4j
@Component
public class AuthUserInfoClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserInfo(
            Long id,
            String email,
            String role,
            // Le endpoint /api/auth/internal/users/{id}/discipline renvoie
            // {"id":..,"email":..,"role":..,"discipline":..} (cf.
            // InternalUserInfoController.UserDisciplineResponse). L'ancien
            // mapping attendait "disciplineDemandee" — bug de naming qui
            // faisait silencieusement tomber la discipline à null et jetait
            // le président en 403 sur l'annuaire de sa discipline.
            @com.fasterxml.jackson.annotation.JsonProperty("discipline")
            String disciplineDemandee,
            String categorieDemandee) {}

    public AuthUserInfoClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/auth/internal";
        this.internalSecret = internalSecret;
    }

    /**
     * Récupère la discipline d'un utilisateur par son id. null si l'utilisateur
     * n'a pas de discipline (ADHERENT, visiteur) ou si auth-service est
     * injoignable.
     */
    public String getDisciplineByUserId(Long userId) {
        if (userId == null) return null;
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        try {
            ResponseEntity<UserInfo> response = restTemplate.exchange(
                    baseUrl + "/users/" + userId + "/discipline",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserInfo.class);
            UserInfo body = response.getBody();
            return body == null ? null : body.disciplineDemandee();
        } catch (Exception e) {
            log.warn("auth-service injoignable pour getDisciplineByUserId({}) : {}",
                    userId, e.getMessage());
            return null;
        }
    }
}
