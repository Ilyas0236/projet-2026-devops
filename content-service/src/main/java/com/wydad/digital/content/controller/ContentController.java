package com.wydad.digital.content.controller;

import com.wydad.digital.content.dto.*;
import com.wydad.digital.content.model.MatchStatut;
import com.wydad.digital.content.model.SportSection;
import com.wydad.digital.content.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ArticleResponse> createArticle(@Valid @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(contentService.createArticle(request));
    }

    @PutMapping("/articles/{id}")
    public ResponseEntity<ArticleResponse> updateArticle(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(contentService.updateArticle(id, request));
    }

    @DeleteMapping("/articles/{id}")
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

    @PostMapping("/matches")
    public ResponseEntity<MatchResponse> createMatch(@Valid @RequestBody MatchRequest request) {
        return ResponseEntity.ok(contentService.createMatch(request));
    }

    @PostMapping("/matches/{id}/result")
    public ResponseEntity<MatchResponse> updateMatchResult(
            @PathVariable Long id,
            @RequestParam(name = "scoreWydad") Integer scoreWydad,
            @RequestParam(name = "scoreAdversaire") Integer scoreAdversaire) {
        return ResponseEntity.ok(contentService.updateMatchResult(id, scoreWydad, scoreAdversaire));
    }

    @DeleteMapping("/matches/{id}")
    public ResponseEntity<Void> deleteMatch(@PathVariable Long id) {
        contentService.deleteMatch(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== CLASSEMENTS (F4) ====================
    @GetMapping("/classements/{competition}")
    public ResponseEntity<List<ClassementResponse>> getClassementsByCompetition(@PathVariable String competition) {
        return ResponseEntity.ok(contentService.getClassementsByCompetition(competition));
    }

    @PostMapping("/classements")
    public ResponseEntity<ClassementResponse> createClassement(@Valid @RequestBody ClassementRequest request) {
        return ResponseEntity.ok(contentService.createClassement(request));
    }

    // ==================== JOUEURS (F5) ====================
    @GetMapping("/joueurs/sport/{sport}")
    public ResponseEntity<List<JoueurResponse>> getJoueursBySport(@PathVariable SportSection sport) {
        return ResponseEntity.ok(contentService.getJoueursBySport(sport));
    }

    @PostMapping("/joueurs")
    public ResponseEntity<JoueurResponse> createJoueur(@Valid @RequestBody JoueurRequest request) {
        return ResponseEntity.ok(contentService.createJoueur(request));
    }
}