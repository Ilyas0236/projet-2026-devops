package com.wydad.digital.content.controller;

import com.wydad.digital.content.config.InternalSecretValidator;
import com.wydad.digital.content.dto.MatchResponse;
import com.wydad.digital.content.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints internes service-à-service du content-service.
 * Protégés par X-Internal-Secret ; la gateway bloque /api/content/internal/**
 * en amont. Utilisés par gamification-service pour valider les pronostics.
 */
@RestController
@RequestMapping("/api/content/internal")
@RequiredArgsConstructor
public class InternalContentController {

    private final ContentService contentService;
    private final InternalSecretValidator secretValidator;

    /** Renvoie le match s'il existe (404 sinon). */
    @GetMapping("/matches/{id}")
    public ResponseEntity<?> getMatchById(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable Long id) {
        if (!secretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return contentService.getMatchById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
