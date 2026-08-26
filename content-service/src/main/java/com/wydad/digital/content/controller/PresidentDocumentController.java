package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.PresidentDocument;
import com.wydad.digital.content.service.PresidentDocumentService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.15 — Workflow de documents internes PRÉSIDENT → ADMIN.
 * Identité du PRÉSIDENT : en-têtes X-User-Id / X-User-Email posés par la
 * gateway (jamais le corps de requête). Lecture publique réservée aux
 * documents PUBLISHED.
 */
@RestController
@RequestMapping("/api/content/president-documents")
@RequiredArgsConstructor
public class PresidentDocumentController {

    private final PresidentDocumentService service;

    // ==================== PRÉSIDENT ====================

    /** Crée un brouillon (PRÉSIDENT). */
    @PostMapping
    @PreAuthorize("hasRole('PRESIDENT')")
    public ResponseEntity<PresidentDocument> createDraft(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody CreateRequest body) {
        PresidentDocument doc = service.createDraft(
                userId, userEmail,
                PresidentDocument.Category.valueOf(body.category()),
                body.title(), body.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    /** Mes documents (PRÉSIDENT). */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('PRESIDENT')")
    public ResponseEntity<List<PresidentDocument>> mine(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(service.mine(userId));
    }

    /** Modifier un brouillon (PRÉSIDENT auteur seulement). */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('PRESIDENT')")
    public ResponseEntity<PresidentDocument> update(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody UpdateRequest body) {
        return ResponseEntity.ok(service.updateDraft(id, userId, body.title(), body.content()));
    }

    /** Soumettre un brouillon à l'ADMIN (PRÉSIDENT). */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('PRESIDENT')")
    public ResponseEntity<PresidentDocument> submit(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(service.submit(id, userId));
    }

    // ==================== ADMIN ====================

    /** File d'attente : tous les SUBMITTED. */
    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PresidentDocument>> pending() {
        return ResponseEntity.ok(service.pendingForAdmin());
    }

    /** Valide un document soumis (ADMIN). */
    @PostMapping("/admin/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PresidentDocument> approve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long adminId,
            @RequestHeader("X-User-Email") String adminEmail) {
        return ResponseEntity.ok(service.approve(id, adminId, adminEmail));
    }

    /** Publie un document validé (ADMIN). */
    @PostMapping("/admin/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PresidentDocument> publish(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long adminId,
            @RequestHeader("X-User-Email") String adminEmail) {
        return ResponseEntity.ok(service.publish(id, adminId, adminEmail));
    }

    /** Refuse un document soumis avec motif obligatoire (ADMIN). */
    @PostMapping("/admin/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PresidentDocument> reject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long adminId,
            @RequestHeader("X-User-Email") String adminEmail,
            @RequestBody @jakarta.validation.Valid RejectRequest body) {
        return ResponseEntity.ok(service.reject(id, adminId, adminEmail, body.motif()));
    }

    // ==================== LECTURE (ADMIN, PRÉSIDENT, MEMBRES AUTH) ====================

    /** Documents publiés (réservés aux utilisateurs authentifiés). */
    @GetMapping("/published")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PresidentDocument>> published() {
        return ResponseEntity.ok(service.published());
    }

    /** Détail d'un document : PUBLISHED pour tous, sinon PRÉSIDENT auteur ou ADMIN. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PresidentDocument> get(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        return ResponseEntity.ok(service.getForRead(id, userId, isAdmin));
    }

    // ==================== DTOs ====================

    public record CreateRequest(
            @NotBlank String category,
            @NotBlank String title,
            @NotBlank String content) {}

    public record UpdateRequest(
            @NotBlank String title,
            @NotBlank String content) {}

    public record RejectRequest(@NotBlank String motif) {}
}
