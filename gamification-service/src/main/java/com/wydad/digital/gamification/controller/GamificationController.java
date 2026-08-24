package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.dto.PredictionRequest;
import com.wydad.digital.gamification.dto.UserPointsDto;
import com.wydad.digital.gamification.filter.UserContext;
import com.wydad.digital.gamification.model.Prediction;
import com.wydad.digital.gamification.model.UserPoints;
import com.wydad.digital.gamification.service.GamificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gamification")
@RequiredArgsConstructor
public class GamificationController {

    private final GamificationService gamificationService;

    @GetMapping("/points/{userId}")
    public ResponseEntity<UserPointsDto> getUserPoints(@PathVariable Long userId) {
        assertSelfOrAdmin(userId);
        return ResponseEntity.ok(gamificationService.getUserPoints(userId));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<UserPoints>> getLeaderboard() {
        return ResponseEntity.ok(gamificationService.getLeaderboard());
    }

    @PostMapping("/predictions")
    public ResponseEntity<Prediction> submitPrediction(@RequestBody PredictionRequest request) {
        // L'utilisateur est TOUJOURS dérivé du JWT : pas de pronostic au nom
        // d'un autre utilisateur, même par erreur d'API.
        request.setUserId(UserContext.getCurrentUserId());
        if (request.getUserId() == null) {
            throw new AccessDeniedException("Utilisateur non authentifié");
        }
        return ResponseEntity.ok(gamificationService.submitPrediction(request));
    }

    @GetMapping("/predictions/user/{userId}")
    public ResponseEntity<List<Prediction>> getUserPredictions(@PathVariable Long userId) {
        assertSelfOrAdmin(userId);
        return ResponseEntity.ok(gamificationService.getUserPredictions(userId));
    }

    /** B.8 : badges possédés par un utilisateur — consultation self ou ADMIN. */
    @GetMapping("/badges/user/{userId}")
    public ResponseEntity<List<com.wydad.digital.gamification.model.UserBadge>> getUserBadges(
            @PathVariable Long userId) {
        assertSelfOrAdmin(userId);
        return ResponseEntity.ok(gamificationService.getUserBadges(userId));
    }
    
    @PostMapping("/points/add")
    public ResponseEntity<String> addPoints(
            @RequestParam Long userId,
            @RequestParam int amount) {

        // S5 : même source de vérité que les autres endpoints — le rôle vient
        // du contexte JWT posé par la gateway, pas d'un header lu à la main.
        if (!UserContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Accès refusé");
        }
        gamificationService.addPoints(userId, amount);
        return ResponseEntity.ok("Points added successfully");
    }

    /** Un utilisateur ne peut consulter que ses points/prédictions ; ADMIN autorisé. */
    private void assertSelfOrAdmin(Long targetUserId) {
        if (!UserContext.isAdmin() && !targetUserId.equals(UserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Accès aux données d'un autre utilisateur interdit");
        }
    }
}
