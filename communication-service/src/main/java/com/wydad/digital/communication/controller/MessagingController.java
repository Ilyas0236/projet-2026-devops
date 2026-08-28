package com.wydad.digital.communication.controller;

import com.wydad.digital.communication.filter.UserContext;
import com.wydad.digital.communication.model.Announcement;
import com.wydad.digital.communication.model.Message;
import com.wydad.digital.communication.repository.MessageRepository;
import com.wydad.digital.communication.service.MessageMediaService;
import com.wydad.digital.communication.service.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Messagerie joueur ↔ staff et annonces (B.5). L'identité vient du
 * contexte JWT ; l'appariement autorisé est vérifié côté serveur.
 *
 * <p>Les chemins conservent le préfixe historique /api/sports/messaging
 * pour compatibilité frontend — la gateway route vers CE service.</p>
 *
 * <p>V2.3 — pièces jointes : upload multipart sur Cloudinary, URL signée
 * à la consultation. Type « authenticated » (pas de hot-link public).</p>
 */
@RestController
@RequestMapping("/api/sports/messaging")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;
    private final MessageMediaService messageMediaService;
    private final MessageRepository messageRepository;

    // ─────────────────────────── MESSAGERIE ───────────────────────────

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PARENT','ADMIN','PRESIDENT')")
    public ResponseEntity<Message> send(
            @RequestParam Long toUserId,
            @RequestBody SendRequest body) {
        MessagingService.Attachment att = (body.attachmentPublicId() == null
                || body.attachmentPublicId().isBlank())
                ? null
                : new MessagingService.Attachment(
                        body.attachmentPublicId(),
                        body.attachmentSecureUrl(),
                        body.attachmentResourceType(),
                        body.attachmentFileName(),
                        body.attachmentSizeBytes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.sendToStaffOrPlayer(toUserId, body.content(), att));
    }

    /**
     * V2.3 — upload d'une pièce jointe (image / PDF / doc, max 10 Mo).
     * Renvoie les métadonnées à passer ensuite à {@code POST /send}.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PARENT','ADMIN','PRESIDENT')")
    public ResponseEntity<MessageMediaService.UploadResult> upload(
            @RequestParam("file") MultipartFile file) throws IOException {
        String email = UserContext.getCurrentUserEmail();
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageMediaService.upload(file, email));
    }

    /**
     * V2.3 — récupère une URL signée fraîche (1 h) pour consulter une
     * pièce jointe. Vérifie que l'appelant est l'un des deux participants
     * de la conversation (anti-IDOR).
     */
    @GetMapping("/attachment/{messageId}")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PARENT','ADMIN','PRESIDENT')")
    public ResponseEntity<Map<String, String>> signedAttachmentUrl(@PathVariable Long messageId) {
        Long me = UserContext.getCurrentUserId();
        if (me == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new AccessDeniedException("Message introuvable"));
        boolean participant = me.equals(m.getSenderUserId()) || me.equals(m.getRecipientUserId())
                || "ADMIN".equalsIgnoreCase(UserContext.getCurrentUserRole());
        if (!participant) {
            throw new AccessDeniedException("Accès refusé à cette pièce jointe");
        }
        if (m.getAttachmentPublicId() == null) {
            return ResponseEntity.ok(Map.of("url", "", "resourceType", ""));
        }
        String resourceType = messageMediaService.detectResourceType(m.getAttachmentSecureUrl());
        // URL stockée récente OU on en regénère une signée.
        String url = messageMediaService.signedUrl(m.getAttachmentPublicId(), resourceType);
        if (url == null) {
            url = m.getAttachmentSecureUrl(); // mode dégradé / dev local
        }
        return ResponseEntity.ok(Map.of(
                "url", url != null ? url : "",
                "resourceType", resourceType));
    }

    @GetMapping("/conversation/{otherUserId}")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PARENT','ADMIN','PRESIDENT')")
    public ResponseEntity<List<Message>> conversation(@PathVariable Long otherUserId) {
        return ResponseEntity.ok(messagingService.getConversationWith(otherUserId));
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PARENT','ADMIN','PRESIDENT')")
    public ResponseEntity<List<Message>> inbox() {
        return ResponseEntity.ok(messagingService.getMyInbox());
    }

    // ──────────────────────────── ANNONCES ────────────────────────────

    /**
     * Publication staff/admin — ciblage optionnel sport + catégorie.
     * Paramètres en STRING (découplage du domaine sportif).
     */
    @PostMapping("/announcements")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<Announcement> publishAnnouncement(
            @RequestBody PublishRequest body,
            @RequestParam(required = false) String sportType,
            @RequestParam(required = false) String category) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.publish(body.title(), body.body(), sportType, category));
    }

    /** Annonces visibles par le connecté : club + SA catégorie (filtrage serveur). */
    @GetMapping("/announcements")
    @PreAuthorize("hasAnyRole('JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PARENT','ADMIN','PRESIDENT')")
    public ResponseEntity<List<Announcement>> visibleAnnouncements() {
        return ResponseEntity.ok(messagingService.getVisibleAnnouncements());
    }

    public record SendRequest(
            String content,
            // V2.3 — pièce jointe optionnelle
            String attachmentPublicId,
            String attachmentSecureUrl,
            String attachmentResourceType,
            String attachmentFileName,
            Long attachmentSizeBytes) {
    }

    public record PublishRequest(String title, String body) {
    }
}
