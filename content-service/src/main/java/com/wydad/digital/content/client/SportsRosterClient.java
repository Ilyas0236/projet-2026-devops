package com.wydad.digital.content.client;

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

import java.util.Map;

/**
 * Client service-à-service vers sports-service : résout le groupe sportif
 * {sportType, category} d'un utilisateur à partir de sa fiche roster
 * (joueur ou staff). Le groupe vient TOUJOURS du serveur (cahier des
 * charges §26) — jamais d'un paramètre falsifiable du client.
 */
@Slf4j
@Component
public class SportsRosterClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String membershipUrl;
    private final String internalSecret;

    public SportsRosterClient(
            @Value("${wydad.sports-service-uri:http://sports-service:8082}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.membershipUrl = baseUrl + "/api/sports/internal/roster/membership/";
        this.internalSecret = internalSecret;
    }

    /** Groupe résolu : sportType, category, rosterRole, fullName. */
    public record Membership(String sportType, String category, String rosterRole, String fullName) {}

    /**
     * Renvoie la fiche d'adhésion de l'utilisateur, ou null s'il n'a aucun
     * profil joueur/staff (VISITEUR, ADHERENT...) ou si sports-service est
     * injoignable — l'appelant doit alors refuser la requête groupée.
     */
    public Membership fetchMembership(Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    membershipUrl + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body == null || body.get("sportType") == null || body.get("category") == null) {
                return null;
            }
            return new Membership(
                    String.valueOf(body.get("sportType")),
                    String.valueOf(body.get("category")),
                    String.valueOf(body.getOrDefault("rosterRole", "")),
                    String.valueOf(body.getOrDefault("fullName", "")));
        } catch (RestClientException e) {
            log.warn("Fiche roster indisponible pour l'utilisateur {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
