package com.wydad.digital.sports.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client service-à-service vers content-service : récupère la fiche d'un
 * match (discipline + catégorie + adversaire). Une convocation de match
 * (§8) doit être liée à un match RÉEL créé par l'ADMIN — la discipline et
 * la catégorie du match font foi, jamais les paramètres du client.
 */
@Slf4j
@Component
public class ContentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String matchUrl;
    private final String internalSecret;

    public ContentClient(
            @Value("${wydad.content-service-uri:http://content-service:8082}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.matchUrl = baseUrl + "/api/content/internal/matches/";
        this.internalSecret = internalSecret;
    }

    /** Fiche minimale du match telle que vue par sports-service. */
    public record MatchInfo(Long id, String adversaire, String sport, String categorie,
                            String lieu) {}

    /**
     * Renvoie le match s'il existe, null sinon ou si content-service est
     * injoignable — l'appelant refuse alors la convocation (pas de
     * convocation sur un match fantôme).
     */
    public MatchInfo fetchMatch(Long matchId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    matchUrl + matchId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body == null || body.get("id") == null) {
                return null;
            }
            return new MatchInfo(
                    ((Number) body.get("id")).longValue(),
                    String.valueOf(body.getOrDefault("adversaire", "")),
                    String.valueOf(body.getOrDefault("sport", "")),
                    body.get("categorie") == null ? null : String.valueOf(body.get("categorie")),
                    String.valueOf(body.getOrDefault("lieu", "")));
        } catch (RestClientException e) {
            log.warn("Match {} indisponible côté content-service : {}", matchId, e.getMessage());
            return null;
        }
    }
}
