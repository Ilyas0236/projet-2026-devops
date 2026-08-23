package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.Reclamation;
import com.wydad.digital.content.service.ReclamationService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.10 — Réclamations & support.
 * Identité du plaignant : en-têtes X-User-Id / X-User-Email posés par la
 * gateway (jamais le corps de requête). Réponse : ADMIN uniquement.
 */
@RestController
@RequestMapping("/api/content/reclamations")
@RequiredArgsConstructor
public class ReclamationController {

    private final ReclamationService reclamationService;

    /** Création — tout utilisateur authentifié (membres et staff). */
    @PostMapping
    public ResponseEntity<Reclamation> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody CreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reclamationService.create(userId, userEmail, body.subject(),
                        body.title(), body.description()));
    }

    /** Mes réclamations — filtrage strict serveur par identité gateway. */
    @GetMapping("/mine")
    public ResponseEntity<List<Reclamation>> mine(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(reclamationService.mine(userId));
    }

    /** ADMIN : toutes les réclamations de tous les membres. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Reclamation>> all() {
        return ResponseEntity.ok(reclamationService.all());
    }

    /** ADMIN : réponse officielle + changement de statut. */
    @PutMapping("/{id}/response")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Reclamation> respond(@PathVariable Long id, @RequestBody RespondRequest body) {
        return ResponseEntity.ok(reclamationService.respond(id, body.response(), body.status()));
    }

    public record CreateRequest(
            @NotBlank Reclamation.Subject subject,
            @NotBlank String title,
            @NotBlank String description) {
    }

    public record RespondRequest(String response, Reclamation.Status status) {
    }
}
