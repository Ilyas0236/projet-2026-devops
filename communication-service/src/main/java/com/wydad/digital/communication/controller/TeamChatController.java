package com.wydad.digital.communication.controller;

import com.wydad.digital.communication.client.RosterClient;
import com.wydad.digital.communication.model.TeamMessage;
import com.wydad.digital.communication.service.TeamChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST du chat de groupe : historique persisté, membres, et fallback
 * d'envoi si le WebSocket est coupé. Mêmes règles d'adhésion que le canal
 * WS (revérifiées côté service).
 *
 * <p>Chemins /api/sports/team-chat conservés pour compatibilité frontend ;
 * la gateway route vers CE service.</p>
 */
@RestController
@RequestMapping("/api/sports/team-chat")
@RequiredArgsConstructor
public class TeamChatController {

    private final TeamChatService teamChatService;

    @GetMapping("/{sport}/{category}/messages")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<List<TeamMessage>> recentMessages(
            @PathVariable String sport,
            @PathVariable String category) {
        return ResponseEntity.ok(teamChatService.getRecentMessages(sport, category));
    }

    @PostMapping("/{sport}/{category}/messages")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<TeamMessage> send(
            @PathVariable String sport,
            @PathVariable String category,
            @RequestBody SendRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamChatService.sendToGroup(sport, category, body.content()));
    }

    @GetMapping("/{sport}/{category}/members")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<List<RosterClient.RosterMember>> members(
            @PathVariable String sport,
            @PathVariable String category) {
        return ResponseEntity.ok(teamChatService.getMembers(sport, category));
    }

    public record SendRequest(String content) {
    }
}
