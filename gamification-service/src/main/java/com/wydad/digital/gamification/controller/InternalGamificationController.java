package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.config.InternalSecretValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints internes service-à-service du gamification-service.
 * Protégés par X-Internal-Secret ; la gateway bloque /api/gamification/internal/**
 * en amont.
 */
@RestController
@RequestMapping("/api/gamification/internal")
@RequiredArgsConstructor
public class InternalGamificationController {

    private final com.wydad.digital.gamification.service.GamificationService gamificationService;
    private final InternalSecretValidator secretValidator;

    /**
     * Résout les pronostics d'un match après saisie du résultat par l'ADMIN
     * côté content-service. Best-effort : renvoie le nombre résolu.
     */
    @PostMapping("/predictions/resolve")
    public ResponseEntity<?> resolvePredictions(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody ResolveRequest request) {
        if (!secretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int resolved = gamificationService.resolvePredictionsForMatch(
                request.matchId(), request.scoreWydad(), request.scoreAdversaire());
        return ResponseEntity.ok(new ResolutionResult(request.matchId(), resolved));
    }

    public record ResolveRequest(Long matchId, Integer scoreWydad, Integer scoreAdversaire) {
    }

    public record ResolutionResult(Long matchId, int resolvedCount) {
    }
}
