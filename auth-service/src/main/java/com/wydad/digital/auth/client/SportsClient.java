package com.wydad.digital.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client interne vers sports-service — à la validation d'un compte sportif
 * (JOUEUR, ENTRAINEUR, STAFF) par l'ADMIN, l'auth-service crée la fiche
 * roster correspondante (players / staff) via l'endpoint interne du
 * sports-service (X-Internal-Secret). Sans cette fiche, l'espace joueur ou
 * staff ne trouve rien : « Impossible de charger votre espace ».
 *
 * <p>Best-effort : un échec n'invalide jamais la décision admin — la fiche
 * reste créable manuellement depuis le back-office.</p>
 */
@Slf4j
@Component
public class SportsClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public SportsClient(
            @Value("${wydad.sports-service-uri:http://sports-service:8087}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/sports/internal/roster";
        this.internalSecret = internalSecret;
    }

    /**
     * Crée (ou met à jour) la fiche roster d'un compte sportif validé.
     * roleDemande : JOUEUR | ENTRAINEUR | STAFF ; discipline/catégorie :
     * celles sollicitées à l'inscription (déjà validées côté inscription).
     * @return true si la fiche est en place.
     */
    public boolean createRosterEntry(Long userId, String fullName,
                                     String roleDemande, String discipline, String categorie) {
        if (discipline == null || categorie == null) {
            log.warn("Fiche roster non créée pour user {} : discipline/catégorie absentes", userId);
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalSecret != null && !internalSecret.isEmpty()) {
                headers.set("X-Internal-Secret", internalSecret);
            }
            Map<String, Object> body;
            if ("JOUEUR".equals(roleDemande)) {
                body = Map.of(
                        "userId", userId,
                        "fullName", fullName,
                        "sportType", discipline,
                        "category", categorie);
            } else {
                // ENTRAINEUR / STAFF : fiche staff — rôle par défaut MANAGER
                // (précisable ensuite par l'ADMIN depuis le back-office).
                body = Map.of(
                        "userId", userId,
                        "fullName", fullName,
                        "role", "MANAGER",
                        "sportType", discipline,
                        "assignedCategory", categorie);
            }
            restTemplate.exchange(
                    baseUrl + ("/JOUEUR".equals(roleDemande) ? "/players" : "/staff"),
                    HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
            return true;
        } catch (Exception e) {
            log.error("Création fiche roster échouée pour user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
