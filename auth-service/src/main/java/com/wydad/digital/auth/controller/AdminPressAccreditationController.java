package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.dto.press.PressAccreditationResponse;
import com.wydad.digital.auth.dto.press.RefusePressAccreditationRequest;
import com.wydad.digital.auth.service.press.PressAccreditationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.17 — Endpoints admin pour traiter la file des demandes d'accréditation.
 *   - GET  /api/auth/admin/press/accreditations/pending : file EN_ATTENTE
 *   - PATCH /api/auth/admin/press/accreditations/{id}/validate : VALIDE
 *   - PATCH /api/auth/admin/press/accreditations/{id}/refuse   : REFUSE + motif
 *
 * Réservé ADMIN : la gateway propage X-User-Role=ADMIN.
 */
@RestController
@RequestMapping("/api/auth/admin/press")
@RequiredArgsConstructor
public class AdminPressAccreditationController {

    private final PressAccreditationService service;

    @GetMapping("/accreditations/pending")
    public ResponseEntity<List<PressAccreditationResponse>> listPending(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (email == null || !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.listPending());
    }

    @PatchMapping("/accreditations/{id}/validate")
    public ResponseEntity<PressAccreditationResponse> validate(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (email == null || !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.validate(id, email));
    }

    @PatchMapping("/accreditations/{id}/refuse")
    public ResponseEntity<PressAccreditationResponse> refuse(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody RefusePressAccreditationRequest request) {
        if (email == null || !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.refuse(id, request.motif(), email));
    }
}
