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
 * Client interne vers sports-service (§24/§26) : récupère les MEMBRES D'UN
 * GROUPE (discipline + catégorie) pour la génération des billets VIP.
 *
 * <p>Un joueur Football U17 ne reçoit JAMAIS de billet pour un match
 * Basketball U17 : la liste vient du roster serveur, pas du client.</p>
 *
 * <p>Best-effort assumé côté appelant : en cas d'indisponibilité, liste vide
 * et journalisation — la génération est idempotente et relançable via
 * /internal/vip-generate ou /vip-distribute (admin).</p>
 *
 * <p>B.29 — la liste inclut désormais JOUEUR + STAFF (qui couvre
 * ENTRAINEUR/MANAGER/FITNESS/etc.) : tous les membres du groupe SENIOR
 * reçoivent 4 billets VIP, pas seulement les joueurs.</p>
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
     * B.29 — Tous les membres d'un groupe {sportType, category} : JOUEUR
     * + STAFF (entraineurs, manager, fitness, etc.). Le quota par membre
     * est appliqué côté {@code VipTicketService}. Liste vide si
     * sports-service est injoignable ou mal configuré — jamais d'exception
     * vers l'appelant (best-effort).
     */
    public List<RosterMember> fetchMembersOfGroup(String sportType, String category) {
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

            List<RosterMember> members = response.getBody() == null
                    ? Collections.emptyList()
                    : response.getBody();
            log.info("sports-service : {} membre(s) dans le groupe {} {}",
                    members.size(), sportType, category);
            return members;
        } catch (Exception e) {
            log.error("sports-service injoignable - génération VIP groupe {} {} impossible",
                    sportType, category, e);
            return Collections.emptyList();
        }
    }

    /**
     * @deprecated depuis B.29 : remplacé par {@link #fetchMembersOfGroup}
     * qui inclut STAFF+ENTRAINEUR. Conservé temporairement pour rétro-compat
     * (tests, autres appelants) — délègue à fetchMembersOfGroup en filtrant
     * JOUEUR pour préserver le comportement initial.
     */
    @Deprecated
    public List<RosterMember> fetchPlayersOfGroup(String sportType, String category) {
        return fetchMembersOfGroup(sportType, category).stream()
                .filter(m -> "JOUEUR".equalsIgnoreCase(m.rosterRole()))
                .toList();
    }
}
