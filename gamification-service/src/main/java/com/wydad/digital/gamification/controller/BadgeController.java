package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.model.BadgeDefinition;
import com.wydad.digital.gamification.service.BadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.8 — Badges de fidélité. Catalogue actif lisible par tout utilisateur
 * authentifié ; écriture ADMIN uniquement. Aucune route d'attribution
 * manuelle : les badges sont attribués automatiquement par le serveur.
 */
@RestController
@RequestMapping("/api/gamification/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    /** Catalogue des badges actifs (utilisateurs authentifiés). */
    @GetMapping
    public ResponseEntity<List<BadgeDefinition>> getActiveBadges() {
        return ResponseEntity.ok(badgeService.getActiveBadges());
    }

    /** Tous les badges, y compris inactifs — ADMIN uniquement. */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BadgeDefinition>> getAllBadges() {
        return ResponseEntity.ok(badgeService.getAllBadges());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BadgeDefinition> create(@RequestBody BadgeRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(badgeService.create(body.code(), body.name(), body.description(), body.minPoints()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BadgeDefinition> update(@PathVariable Long id, @RequestBody BadgeRequest body) {
        return ResponseEntity.ok(badgeService.update(id, body.name(), body.description(),
                body.minPoints(), body.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        badgeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record BadgeRequest(String code, String name, String description, Integer minPoints, Boolean active) {
    }
}
