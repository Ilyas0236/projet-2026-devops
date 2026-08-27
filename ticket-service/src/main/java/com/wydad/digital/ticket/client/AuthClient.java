package com.wydad.digital.ticket.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

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
    private final String isAdherentUrl;
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
        this.isAdherentUrl = baseUrl + "/api/auth/subscriptions/internal/is-adherent";
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
     * B.12 — Indique si l'utilisateur (par email) a un abonnement saisonnier
     * ACTIF non expiré. Utilisé pour la fenêtre 48h prioritaire des matchs
     * EXCEPTIONNELS (LDC, demi-finales…). Renvoie false en cas d'erreur
     * réseau : on préfère ne PAS bloquer l'achat si auth-service est
     * injoignable (best-effort), mais on le journalise.
     */
    public boolean isActiveAdherent(String email) {
        if (email == null || email.isBlank()) return false;
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        try {
            ResponseEntity<Boolean> response = restTemplate.exchange(
                    isAdherentUrl + "?email=" + email,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            log.warn("auth-service injoignable pour is-adherent({}) : {}", email, e.getMessage());
            return false;
        }
    }
}
