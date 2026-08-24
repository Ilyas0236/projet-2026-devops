package com.wydad.digital.sports.ws;

import com.wydad.digital.sports.config.TeamChatAuthInterceptor.TeamChatPrincipal;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.TeamMessage;
import com.wydad.digital.sports.service.TeamChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 4 — contrôleur WebSocket du chat de groupe.
 *
 * <pre>
 * Client -> /app/chat/{sport}/{categorie}/send   { content }
 * Serveur -> /topic/chat/{sport}/{categorie}     TeamMessage
 * </pre>
 *
 * L'identité vient du Principal posé au CONNECT (JWT validé par
 * {@link com.wydad.digital.sports.config.TeamChatAuthInterceptor}) ;
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

        TeamMessage saved = teamChatService.sendToGroup(
                SportType.valueOf(sport.toUpperCase()),
                Category.valueOf(category.toUpperCase()),
                payload.content());

        String topic = "/topic/chat/" + sport.toLowerCase() + "/" + category.toLowerCase();
        messagingTemplate.convertAndSend(topic, saved);

        teamChatService.notifyOfflineMembers(saved,
                onlineByGroup.getOrDefault(groupKey(sport, category), Set.of()));
    }

    /** Le client signale son arrivée/départ pour le suivi « en ligne ». */
    @MessageMapping("/chat/{sport}/{category}/presence")
    public void presence(@DestinationVariable String sport,
                         @DestinationVariable String category,
                         @Payload PresencePayload payload,
                         Principal principal) {
        TeamChatPrincipal me = (TeamChatPrincipal) principal;
        if (me == null) { return; }
        String key = groupKey(sport, category);
        var sessions = onlineByGroup.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        if (payload.online()) {
            sessions.add(me.userId());
        } else {
            sessions.remove(me.userId());
        }
    }

    private String groupKey(String sport, String category) {
        return sport.toLowerCase() + ":" + category.toLowerCase();
    }

    public record ChatPayload(String content) {
    }

    public record PresencePayload(boolean online) {
    }
}
