package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.dto.press.PressAccreditationRequest;
import com.wydad.digital.auth.dto.press.PressAccreditationResponse;
import com.wydad.digital.auth.service.press.PressAccreditationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.17 — Endpoints journaliste pour gérer ses accréditations.
 *   - POST /api/auth/presse/accreditations       : créer une demande
 *   - GET  /api/auth/presse/accreditations/me    : lister ses demandes
 *   - GET  /api/auth/presse/accreditations/{id}/badge : télécharger le badge PDF
 *     (uniquement pour les demandes VALIDÉES ; self ou ADMIN)
 *
 * Garde de sécurité : email + role injectés par la gateway via X-User-* ;
 * aucun endpoint n'accepte de paramètre "email" en query/body.
 */
@RestController
@RequestMapping("/api/auth/presse")
@RequiredArgsConstructor
public class PressAccreditationController {

    private final PressAccreditationService service;

    @PostMapping("/accreditations")
    public ResponseEntity<PressAccreditationResponse> create(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody PressAccreditationRequest request) {
        if (email == null || role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"JOURNALISTE".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        PressAccreditationResponse resp = service.createAccreditation(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/accreditations/me")
    public ResponseEntity<List<PressAccreditationResponse>> listMine(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (email == null || role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"JOURNALISTE".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.listMine(email));
    }

    @GetMapping("/accreditations/{id}/badge")
    public ResponseEntity<byte[]> downloadBadge(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role) throws Exception {
        if (email == null || role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean isAdmin = "ADMIN".equals(role);
        // Un journaliste peut aussi demander son propre badge (rôle doit
        // être JOURNALISTE ou ADMIN — un ADHERENT n'a rien à faire ici).
        if (!isAdmin && !"JOURNALISTE".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] pdf = service.generateBadgeFor(id, email, isAdmin);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=badge-accreditation-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
