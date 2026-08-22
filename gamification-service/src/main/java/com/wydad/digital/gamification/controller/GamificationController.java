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
        return ResponseEntity.ok(gamificationService.submitPrediction(request));
    }

    @GetMapping("/predictions/user/{userId}")
    public ResponseEntity<List<Prediction>> getUserPredictions(@PathVariable Long userId) {
        assertSelfOrAdmin(userId);
        return ResponseEntity.ok(gamificationService.getUserPredictions(userId));
    }
    
    @PostMapping("/points/add")
    public ResponseEntity<String> addPoints(
            @RequestParam Long userId,
            @RequestParam int amount,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        // Réservé aux appels administrateur (ou futurs appels service-à-service authentifiés)
        if (!"ADMIN".equals(role)) {
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
