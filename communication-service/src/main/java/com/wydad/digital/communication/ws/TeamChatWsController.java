package com.wydad.digital.communication.ws;

import com.wydad.digital.communication.config.TeamChatAuthInterceptor.TeamChatPrincipal;
import com.wydad.digital.communication.filter.UserContext;
import com.wydad.digital.communication.model.TeamMessage;
import com.wydad.digital.communication.service.TeamChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrôleur WebSocket du chat de groupe.
 *
 * <pre>
 * Client -> /app/chat/{sport}/{categorie}/send   { content }
 * Serveur -> /topic/chat/{sport}/{categorie}     TeamMessage
 * </pre>
 *
 * L'identité vient du Principal posé au CONNECT (JWT validé par
 * {@link com.wydad.digital.communication.config.TeamChatAuthInterceptor}) ;
 * l'adhésion est revérifiée dans {@link TeamChatService} à chaque envoi.
 * Les membres en ligne reçoivent la diffusion temps réel ; les absents
 * reçoivent une notification in-app (best-effort).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class TeamChatWsController {

    private final TeamChatService teamChatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.wydad.digital.communication.client.RosterClient rosterClient;

    /**
     * Sessions actuellement connectées PAR GROUPE — sert à n'envoyer des
     * notifications in-app qu'aux membres hors ligne.
     */
    private final ConcurrentHashMap<String, Set<Long>> onlineByGroup = new ConcurrentHashMap<>();

    @MessageMapping("/chat/{sport}/{category}/send")
    public void send(@DestinationVariable String sport,
                     @DestinationVariable String category,
                     @Payload ChatPayload payload,
                     Principal principal) {
        TeamChatPrincipal me = (TeamChatPrincipal) principal;
        if (me == null) {
            throw new IllegalArgumentException("Session non authentifiée");
        }

        // Sur le canal WS il n'y a pas de filtre HTTP : le contexte vient du
        // JWT validé au CONNECT (principal). On le projette dans le ThreadLocal
        // que lit TeamChatService (adhésion + nom), puis on nettoie.
        UserContext.setCurrentUserId(me.userId());
        UserContext.setCurrentUserRole(me.role());
        try {
            TeamMessage saved = teamChatService.sendToGroup(sport, category, payload.content());

            String topic = "/topic/chat/" + sport.toLowerCase() + "/" + category.toLowerCase();
            messagingTemplate.convertAndSend(topic, saved);

            teamChatService.notifyOfflineMembers(saved,
                    onlineByGroup.getOrDefault(groupKey(sport, category), Set.of()));
        } finally {
            UserContext.clear();
        }
    }

    /**
     * Le client signale son arrivée/départ pour le suivi « en ligne ».
     * Phase 5 — l'adhésion au groupe est vérifiée ici aussi : sans ce
     * contrôle, un compte authentifié hors équipe pourrait s'annoncer en
     * ligne sur le topic d'une autre équipe (et fausser les notifications).
     */
    @MessageMapping("/chat/{sport}/{category}/presence")
    public void presence(@DestinationVariable String sport,
                         @DestinationVariable String category,
                         @Payload PresencePayload payload,
                         Principal principal) {
        TeamChatPrincipal me = (TeamChatPrincipal) principal;
        if (me == null) { return; }
        if (!isMemberOfGroup(me, sport, category)) {
            log.warn("Presence refusée : user {} hors du groupe {}:{}",
                    me.userId(), sport, category);
            return;
        }
        String key = groupKey(sport, category);
        var sessions = onlineByGroup.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        if (payload.online()) {
            sessions.add(me.userId());
        } else {
            sessions.remove(me.userId());
        }
    }

    /**
     * Adhésion au groupe demandé — même règle que le SUBSCRIBE de
     * {@link com.wydad.digital.communication.config.TeamChatAuthInterceptor} :
     * fiche roster JOUEUR ou STAFF correspondant au sport+catégorie
     * (ADMIN : supervision).
     */
    private boolean isMemberOfGroup(TeamChatPrincipal me, String sport, String category) {
        if ("ADMIN".equals(me.role())) {
            return true; // supervision
        }
        var mine = rosterClient.findMembership(me.userId());
        return mine != null
                && ("JOUEUR".equals(mine.rosterRole()) || "STAFF".equals(mine.rosterRole()))
                && sport.equalsIgnoreCase(mine.sportType())
                && category.equalsIgnoreCase(mine.category());
    }

    private String groupKey(String sport, String category) {
        return sport.toLowerCase() + ":" + category.toLowerCase();
    }

    public record ChatPayload(String content) {
    }

    public record PresencePayload(boolean online) {
    }
}
