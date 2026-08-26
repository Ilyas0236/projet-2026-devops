package com.wydad.digital.ticket.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client service-à-service vers auth-service pour récupérer la liste des
 * joueurs actifs (rôle JOUEUR, compte VALIDÉ) — utilisée par la génération
 * automatique des billets VIP à domicile (Phase 2).
 *
 * Best-effort assumé côté appelant : en cas d'indisponibilité, la liste
 * retournée est vide et l'appelant journalise (pas de demi-génération
 * silencieuse — le déclencheur peut relancer, la génération est idempotente).
 */
@Slf4j
@Component
public class AuthClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String recipientsUrl;
    private final String visitorUrl;
    private final String internalSecret;

    /** Projection minimale de UserProfileResponse (auth-service). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerRecipient(
            Long id,
            String email,
            String firstName,
            String lastName,
            String role,
            String statutCompte,
            boolean active) {

        public String displayName() {
            return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""))
                    .trim().isEmpty() ? email : (firstName + " " + lastName).trim();
        }
    }

    public AuthClient(
            @Value("${wydad.auth-service-uri:http://auth-service:8081}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.recipientsUrl = baseUrl + "/api/auth/internal/recipients";
        this.visitorUrl = baseUrl + "/api/auth/internal/visitors";
        this.internalSecret = internalSecret;
    }

    /**
     * Liste des joueurs éligibles aux billets VIP : rôle JOUEUR, compte
     * actif et VALIDÉ. Liste vide si auth-service est injoignable ou mal
     * configuré (secret) — jamais d'exception vers l'appelant.
     */
    public List<PlayerRecipient> fetchActivePlayers() {
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }

        try {
            ResponseEntity<List<PlayerRecipient>> response = restTemplate.exchange(
                    recipientsUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<PlayerRecipient>>() {});

            List<PlayerRecipient> players = response.getBody() == null
                    ? Collections.emptyList()
                    : response.getBody().stream()
                            .filter(u -> "JOUEUR".equalsIgnoreCase(u.role()))
                            .filter(u -> u.active())
                            .filter(u -> "VALIDE".equalsIgnoreCase(u.statutCompte()))
                            .toList();
            log.info("auth-service : {} joueur(s) actif(s) éligible(s) VIP", players.size());
            return players;
        } catch (Exception e) {
            log.error("auth-service injoignable ({}) - aucune génération VIP possible", recipientsUrl, e);
            return Collections.emptyList();
        }
    }

    /**
     * B.28 — Crée (ou récupère) un user VISITEUR à la volée.
     * Appelé depuis l'endpoint public d'achat sans compte.
     * Retourne null si auth-service est injoignable ou refuse l'appel :
     * l'appelant doit alors refuser l'achat (on ne crée PAS de ticket
     * orphelin, on ne contourne pas la traçabilité userId).
     */
    public PlayerRecipient createOrFetchVisitor(String email, String firstName, String lastName, String phone) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        Map<String, String> body = Map.of(
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "phone", phone
        );
        try {
            ResponseEntity<PlayerRecipient> response = restTemplate.exchange(
                    visitorUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    PlayerRecipient.class);
            PlayerRecipient visitor = response.getBody();
            if (visitor != null) {
                log.info("VISITEUR créé/récupéré : id={} email={}", visitor.id(), visitor.email());
            }
            return visitor;
        } catch (Exception e) {
            log.error("auth-service injoignable ({}) - achat visiteur impossible", visitorUrl, e);
            return null;
        }
    }
}
