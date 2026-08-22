package com.wydad.digital.content.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client best-effort vers gamification-service : notifie la résolution des
 * pronostics après saisie d'un résultat de match. Une panne de gamification
 * ne doit JAMAIS empêcher l'enregistrement du résultat.
 */
@Slf4j
@Component
public class GamificationClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String resolveUrl;
    private final String internalSecret;

    public GamificationClient(
            @Value("${wydad.gamification-service-uri:http://gamification-service:8088}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.resolveUrl = baseUrl + "/api/gamification/internal/predictions/resolve";
        this.internalSecret = internalSecret;
    }

    /**
     * Notifie gamification-service qu'un résultat a été saisi.
     * Journalise et ignore toute erreur (best-effort).
     */
    public void notifyMatchResult(Long matchId, Integer scoreWydad, Integer scoreAdversaire) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }

            var body = new java.util.HashMap<String, Object>();
            body.put("matchId", matchId);
            body.put("scoreWydad", scoreWydad);
            body.put("scoreAdversaire", scoreAdversaire);

            restTemplate.postForEntity(resolveUrl,
                    new HttpEntity<>(body, headers), String.class);
            log.info("Pronostics resolus pour le match {}", matchId);
        } catch (RestClientException e) {
            log.warn("Resolution des pronostics impossible pour le match {}: {}",
                    matchId, e.getMessage());
        }
    }
}
