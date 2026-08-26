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
 * Client interne vers sports-service (§24/§26) : récupère les joueurs D'UN
 * GROUPE (discipline + catégorie) pour la génération des billets VIP.
 *
 * <p>Un joueur Football U17 ne reçoit JAMAIS de billet pour un match
 * Basketball U17 : la liste vient du roster serveur, pas du client.</p>
 *
 * <p>Best-effort assumé côté appelant : en cas d'indisponibilité, liste vide
 * et journalisation — la génération est idempotente et relançable via
 * /internal/vip-generate.</p>
 */
@Slf4j
@Component
public class SportsRosterClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String membersUrlTemplate;
    private final String internalSecret;

    /** Membre du roster renvoyé par /api/sports/internal/roster/members. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RosterMember(Long userId, String fullName, String rosterRole) {}

    public SportsRosterClient(
            @Value("${wydad.sports-service-uri:http://sports-service:8087}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.membersUrlTemplate = baseUrl + "/api/sports/internal/roster/members"
                + "?sportType={sport}&category={category}";
        this.internalSecret = internalSecret;
    }

    /**
     * Joueurs (et uniquement les joueurs, pas le staff) d'un groupe
     * {sportType, category}. Liste vide si sports-service est injoignable ou
     * mal configuré — jamais d'exception vers l'appelant.
     */
    public List<RosterMember> fetchPlayersOfGroup(String sportType, String category) {
        HttpHeaders headers = new HttpHeaders();
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }

        try {
            ResponseEntity<List<RosterMember>> response = restTemplate.exchange(
                    membersUrlTemplate,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<RosterMember>>() {},
                    sportType, category);

            List<RosterMember> players = response.getBody() == null
                    ? Collections.emptyList()
                    : response.getBody().stream()
                            .filter(m -> "JOUEUR".equalsIgnoreCase(m.rosterRole()))
                            .toList();
            log.info("sports-service : {} joueur(s) dans le groupe {} {}",
                    players.size(), sportType, category);
            return players;
        } catch (Exception e) {
            log.error("sports-service injoignable - génération VIP groupe {} {} impossible",
                    sportType, category, e);
            return Collections.emptyList();
        }
    }
}
