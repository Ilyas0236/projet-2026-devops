package com.wydad.digital.communication.controller;

import com.wydad.digital.communication.client.RosterClient;
import com.wydad.digital.communication.filter.UserContext;
import com.wydad.digital.communication.model.TeamMessage;
import com.wydad.digital.communication.service.MessageMediaService;
import com.wydad.digital.communication.service.TeamChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final MessageMediaService messageMediaService;

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

    /**
     * Envoi d'un message avec pièce jointe (photo dans le chat groupe —
     * usage principal : président qui envoie une convocation visuelle à
     * toute une équipe). Upload Cloudinary, puis persistance du message.
     * Limite 10 Mo, mêmes règles d'adhésion que l'envoi texte.
     */
    @PostMapping(value = "/{sport}/{category}/media", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<TeamMessage> sendWithMedia(
            @PathVariable String sport,
            @PathVariable String category,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false, defaultValue = "") String caption,
            @RequestParam(value = "mediaType", required = false, defaultValue = "IMAGE") String mediaType) {
        Long me = UserContext.getCurrentUserId();
        if (me == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        String email = UserContext.getCurrentUserEmail() != null
                ? UserContext.getCurrentUserEmail() : "anon";
        MessageMediaService.UploadResult uploaded;
        try {
            uploaded = messageMediaService.upload(file, email);
        } catch (IOException e) {
            throw new RuntimeException("Échec de l'upload média : " + e.getMessage(), e);
        }
        // En mode local (sans Cloudinary) l'URL reste un placeholder : on
        // utilise l'identifiant local: comme mediaUrl (l'UI saura afficher
        // « pièce jointe (mode local) »).
        String mediaUrl = uploaded.secureUrl() != null
                ? uploaded.secureUrl()
                : "local:" + uploaded.publicId();
        // Texte obligatoire comme légende (cohérent WhatsApp).
        String content = caption == null || caption.isBlank()
                ? "[" + mediaType + "]"
                : caption;
        TeamMessage saved = teamChatService.sendToGroupWithMedia(
                sport, category, content, mediaUrl, mediaType);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    public record SendRequest(String content) {
    }
}
