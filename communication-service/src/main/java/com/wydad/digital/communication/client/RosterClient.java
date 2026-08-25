package com.wydad.digital.communication.client;

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

import java.util.List;

/**
 * Client vers l'API interne « roster » de sports-service — la SEULE
 * dépendance de communication-service envers le domaine sportif.
 *
 * <p>communication-service ne doit pas accéder aux tables players/staff :
 * il demande à sports-service « qui appartient à quelle catégorie ».
 * Les appels sont signés par X-Internal-Secret (comparaison à temps
 * constant côté récepteur) et la gateway bloque toute route /internal/**.</p>
 */
@Slf4j
@Component
public class RosterClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalSecret;

    public RosterClient(
            @Value("${wydad.sports-service-uri:http://sports-service:8087}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.baseUrl = baseUrl + "/api/sports/internal/roster";
        this.internalSecret = internalSecret;
    }

    /**
     * Fiche d'adhésion d'un utilisateur : { userId, sportType, category,
     * rosterRole (JOUEUR|STAFF), fullName } — null si aucune fiche joueur
     * ou staff ne lui correspond (visiteur, parent…).
     */
    public MembershipInfo findMembership(Long userId) {
        try {
            ResponseEntity<MembershipInfo> response = restTemplate.exchange(
                    baseUrl + "/membership/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    MembershipInfo.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Roster indisponible pour user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Membres d'un groupe {sport, category} : joueurs + staff encadrant,
     * vide si erreur ou groupe inexistant.
     */
    public List<RosterMember> findGroupMembers(String sportType, String category) {
        try {
            ResponseEntity<List<RosterMember>> response = restTemplate.exchange(
                    baseUrl + "/members?sportType=" + sportType + "&category=" + category,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<List<RosterMember>>() {
                    });
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            log.warn("Roster indisponible pour groupe {} {}: {}", sportType, category, e.getMessage());
            return List.of();
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }
        return headers;
    }

    /** Fiche roster individuelle (joueur OU staff). */
    public record MembershipInfo(Long userId, String sportType, String category,
                                 String rosterRole, String fullName) {
    }

    /** Membre d'un groupe (en-tête du chat, notifications hors ligne). */
    public record RosterMember(Long userId, String fullName, String rosterRole) {
    }
}
