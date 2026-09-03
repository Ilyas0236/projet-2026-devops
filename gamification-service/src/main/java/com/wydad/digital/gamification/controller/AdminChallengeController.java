package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.model.Challenge;
import com.wydad.digital.gamification.service.ChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * B.8.c — Pilotage admin des défis temporels.
 *
 * CRUD complet (création, édition, désactivation, suppression) + endpoint
 * d'évaluation forcée (utile quand l'admin ferme la fenêtre manuellement
 * ou que le cron n'est pas encore passé).
 *
 * ADMIN uniquement.
 */
@RestController
@RequestMapping("/api/gamification/admin/challenges")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChallengeController {

    private final ChallengeService challengeService;

    @GetMapping
    public ResponseEntity<List<Challenge>> list() {
        return ResponseEntity.ok(challengeService.getAllChallenges());
    }

    @PostMapping
    public ResponseEntity<Challenge> create(@RequestBody Challenge challenge) {
        return ResponseEntity.ok(challengeService.create(challenge));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Challenge> update(@PathVariable Long id, @RequestBody Challenge patch) {
        return ResponseEntity.ok(challengeService.update(id, patch));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        challengeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Force l'évaluation immédiate de tous les défis ouverts.
     * Renvoie le nombre d'utilisateurs impactés (récompenses créditées).
     */
    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        int touched = challengeService.evaluateAll();
        return ResponseEntity.ok(Map.of("touched", touched));
    }
}
