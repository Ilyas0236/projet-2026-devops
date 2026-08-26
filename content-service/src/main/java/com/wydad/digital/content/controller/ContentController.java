package com.wydad.digital.content.controller;

import com.wydad.digital.content.dto.*;
import com.wydad.digital.content.model.MatchStatut;
import com.wydad.digital.content.model.SportSection;
import com.wydad.digital.content.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    // ==================== ARTICLES (F1 - Actualités) ====================
    @GetMapping("/articles")
    public ResponseEntity<List<ArticleResponse>> getAllArticles() {
        return ResponseEntity.ok(contentService.getAllArticles());
    }

    @GetMapping("/articles/sport/{sport}")
    public ResponseEntity<List<ArticleResponse>> getArticlesBySport(@PathVariable SportSection sport) {
        return ResponseEntity.ok(contentService.getArticlesBySport(sport));
    }

    @GetMapping("/articles/{id}")
    public ResponseEntity<ArticleResponse> getArticleById(@PathVariable Long id) {
        return ResponseEntity.ok(contentService.getArticleById(id));
    }

    @PostMapping("/articles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ArticleResponse> createArticle(@Valid @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(contentService.createArticle(request));
    }

    @PutMapping("/articles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ArticleResponse> updateArticle(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(contentService.updateArticle(id, request));
    }

    @DeleteMapping("/articles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteArticle(@PathVariable Long id) {
        contentService.deleteArticle(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== MATCHS (F2 - Calendrier, F3 - Résultats) ====================
    @GetMapping("/matches")
    public ResponseEntity<List<MatchResponse>> getAllMatches() {
        return ResponseEntity.ok(contentService.getAllMatches());
    }

    @GetMapping("/matches/statut/{statut}")
    public ResponseEntity<List<MatchResponse>> getMatchesByStatut(@PathVariable MatchStatut statut) {
        return ResponseEntity.ok(contentService.getMatchesByStatut(statut));
    }

    /**
     * Matchs du groupe de l'utilisateur connecté (joueur/staff) — le groupe
     * est résolu côté serveur depuis sa fiche roster (§16/§26). Les
     * visiteurs obtiennent une liste vide (pas d'erreur).
     */
    @GetMapping("/matches/mine")
    public ResponseEntity<List<MatchResponse>> getMyMatches(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        boolean adminOrPresident = "ADMIN".equals(role) || "PRESIDENT".equals(role);
        if (userId == null || userId <= 0 || (role == null)) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(contentService.getMatchesForCurrentUser(userId, adminOrPresident));
    }

    @PostMapping("/matches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MatchResponse> createMatch(@Valid @RequestBody MatchRequest request) {
        return ResponseEntity.ok(contentService.createMatch(request));
    }

    @PostMapping("/matches/{id}/result")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MatchResponse> updateMatchResult(
            @PathVariable Long id,
            @RequestParam(name = "scoreWydad") Integer scoreWydad,
            @RequestParam(name = "scoreAdversaire") Integer scoreAdversaire) {
        return ResponseEntity.ok(contentService.updateMatchResult(id, scoreWydad, scoreAdversaire));
    }

    @PutMapping("/matches/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MatchResponse> updateMatch(@PathVariable Long id, @Valid @RequestBody MatchRequest request) {
        return ResponseEntity.ok(contentService.updateMatch(id, request));
    }

    @DeleteMapping("/matches/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMatch(@PathVariable Long id) {
        contentService.deleteMatch(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== CLASSEMENTS (F4) ====================
    /**
     * S7 : lecture publique, cohérente avec /classements/{competition} —
     * un classement est une donnée publique affichée aux visiteurs ; seules
     * les écritures (POST/PUT/DELETE) sont réservées à l'ADMIN.
     */
    @GetMapping("/classements")
    public ResponseEntity<List<ClassementResponse>> getAllClassements() {
        return ResponseEntity.ok(contentService.getAllClassements());
    }

    @GetMapping("/classements/{competition}")
    public ResponseEntity<List<ClassementResponse>> getClassementsByCompetition(@PathVariable String competition) {
        return ResponseEntity.ok(contentService.getClassementsByCompetition(competition));
    }

    @PostMapping("/classements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassementResponse> createClassement(@Valid @RequestBody ClassementRequest request) {
        return ResponseEntity.ok(contentService.createClassement(request));
    }

    @PutMapping("/classements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassementResponse> updateClassement(@PathVariable Long id, @Valid @RequestBody ClassementRequest request) {
        return ResponseEntity.ok(contentService.updateClassement(id, request));
    }

    @DeleteMapping("/classements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClassement(@PathVariable Long id) {
        contentService.deleteClassement(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== JOUEURS (F5) ====================
    @GetMapping("/joueurs/sport/{sport}")
    public ResponseEntity<List<JoueurResponse>> getJoueursBySport(@PathVariable SportSection sport) {
        return ResponseEntity.ok(contentService.getJoueursBySport(sport));
    }

    @PostMapping("/joueurs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JoueurResponse> createJoueur(@Valid @RequestBody JoueurRequest request) {
        return ResponseEntity.ok(contentService.createJoueur(request));
    }

    @PutMapping("/joueurs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JoueurResponse> updateJoueur(@PathVariable Long id, @Valid @RequestBody JoueurRequest request) {
        return ResponseEntity.ok(contentService.updateJoueur(id, request));
    }

    @DeleteMapping("/joueurs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteJoueur(@PathVariable Long id) {
        contentService.deleteJoueur(id);
        return ResponseEntity.noContent().build();
    }
}