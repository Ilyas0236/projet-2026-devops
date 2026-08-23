package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Announcement;
import com.wydad.digital.sports.model.Message;
import com.wydad.digital.sports.service.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Messagerie joueur ↔ staff et annonces (B.5). L'identité vient du
 * contexte JWT ; l'appariement autorisé est vérifié côté serveur.
 */
@RestController
@RequestMapping("/api/sports/messaging")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;

    // ─────────────────────────── MESSAGERIE ───────────────────────────

    @PostMapping("/send")
    @PreAuthorize("hasRole('JOUEUR') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<Message> send(
            @RequestParam Long toUserId,
            @RequestBody SendRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.sendToStaffOrPlayer(toUserId, body.content()));
    }

    @GetMapping("/conversation/{otherUserId}")
    @PreAuthorize("hasRole('JOUEUR') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<Message>> conversation(@PathVariable Long otherUserId) {
        return ResponseEntity.ok(messagingService.getConversationWith(otherUserId));
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasRole('JOUEUR') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<Message>> inbox() {
        return ResponseEntity.ok(messagingService.getMyInbox());
    }

    // ──────────────────────────── ANNONCES ────────────────────────────

    /** Publication staff/admin — ciblage optionnel sport + catégorie. */
    @PostMapping("/announcements")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<Announcement> publishAnnouncement(
            @RequestBody PublishRequest body,
            @RequestParam(required = false) SportType sportType,
            @RequestParam(required = false) Category category) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.publish(body.title(), body.body(), sportType, category));
    }

    /** Annonces visibles par le connecté : club + SA catégorie (filtrage serveur). */
    @GetMapping("/announcements")
    @PreAuthorize("hasRole('JOUEUR') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<Announcement>> visibleAnnouncements() {
        return ResponseEntity.ok(messagingService.getVisibleAnnouncements());
    }

    public record SendRequest(String content) {
    }

    public record PublishRequest(String title, String body) {
    }
}
