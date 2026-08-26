package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.MatchConvocationDtos.BatchMatchConvocationRequest;
import com.wydad.digital.sports.dto.MatchConvocationDtos.BatchResult;
import com.wydad.digital.sports.dto.MatchConvocationDtos.MatchConvocationResponse;
import com.wydad.digital.sports.dto.MatchConvocationDtos.PublicConvocationView;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.service.MatchConvocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Convocations de match (§8/§9) : préparation par l'entraîneur du groupe,
 * soumission à l'ADMIN, publication sur le site public. L'isolation
 * discipline+catégorie est appliquée serveur-side dans le service.
 */
@RestController
@RequestMapping("/api/sports/match-convocations")
@RequiredArgsConstructor
public class MatchConvocationController {

    private final MatchConvocationService service;

    // ────────────────────── ENTRAÎNEUR / STAFF (§8) ──────────────────────

    /** Joueurs sélectionnables pour un match (groupe du match uniquement). */
    @GetMapping("/match/{matchId}/selectable")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<List<Player>> selectable(@PathVariable Long matchId) {
        return ResponseEntity.ok(service.selectablePlayers(matchId));
    }

    /** Convocation groupée (liste cochable, rôles titulaire/remplaçant). */
    @PostMapping("/match/{matchId}")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<BatchResult> convocateBatch(
            @PathVariable Long matchId,
            @RequestBody BatchMatchConvocationRequest request) {
        BatchMatchConvocationRequest scoped =
                new BatchMatchConvocationRequest(matchId, request.players());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.convocateBatch(scoped));
    }

    /** Feuille de match (vue encadrement du groupe). */
    @GetMapping("/match/{matchId}")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<List<MatchConvocationResponse>> sheet(@PathVariable Long matchId) {
        return ResponseEntity.ok(service.sheetForStaff(matchId));
    }

    /** Soumission de la feuille à l'ADMIN (§9). */
    @PostMapping("/match/{matchId}/submit")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<List<MatchConvocationResponse>> submit(@PathVariable Long matchId) {
        return ResponseEntity.ok(service.submitToAdmin(matchId));
    }

    /** Convocations du joueur connecté (son propre espace, §8). */
    @GetMapping("/my")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<List<MatchConvocationResponse>> my() {
        return ResponseEntity.ok(service.mySheet(SportsUserContext.getCurrentUserId()));
    }

    // ────────────────────── ADMIN (§9) ──────────────────────

    /** Feuilles soumises en attente de décision ADMIN. */
    @GetMapping("/admin/submitted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MatchConvocationResponse>> submitted() {
        return ResponseEntity.ok(service.submittedSheets());
    }

    /** Publication de la feuille d'un match (site public, §9). */
    @PostMapping("/admin/match/{matchId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MatchConvocationResponse>> publish(@PathVariable Long matchId) {
        return ResponseEntity.ok(service.publish(matchId));
    }

    /** Refus motivé de la feuille d'un match. */
    @PostMapping("/admin/match/{matchId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MatchConvocationResponse>> reject(
            @PathVariable Long matchId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(service.reject(matchId, reason));
    }

    // ────────────────────── PUBLIC (§9) ──────────────────────

    /**
     * Liste publiée d'un match — route publique : la gateway laisse passer
     * les GET ; le service ne renvoie QUE les feuilles PUBLIEES.
     */
    @GetMapping("/public/match/{matchId}")
    public ResponseEntity<PublicConvocationView> publicView(@PathVariable Long matchId) {
        return ResponseEntity.ok(service.publicView(matchId));
    }
}
