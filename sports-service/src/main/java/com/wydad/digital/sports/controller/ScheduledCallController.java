package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.model.ScheduledCall;
import com.wydad.digital.sports.service.ScheduledCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 5 — appels vidéo/vocaux programmés.
 * Création : ENTRAINEUR / PRESIDENT / ADMIN uniquement (re-vérifié dans le
 * service). Lecture/jeton : organisateur ou participant (liste fermée).
 */
@RestController
@RequestMapping("/api/sports/calls")
@RequiredArgsConstructor
public class ScheduledCallController {

    private final ScheduledCallService callService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ENTRAINEUR','PRESIDENT','ADMIN')")
    public ResponseEntity<ScheduledCall> create(@RequestBody ScheduledCallService.CreateCallRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(callService.createCall(req));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ScheduledCall>> mine() {
        return ResponseEntity.ok(callService.getMyCalls());
    }

    /** Jeton de connexion média — organisateur ou participant uniquement. */
    @PostMapping("/{id}/token")
    public ResponseEntity<ScheduledCallService.CallToken> token(@PathVariable Long id) {
        return ResponseEntity.ok(callService.joinToken(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','PRESIDENT','ADMIN')")
    public ResponseEntity<ScheduledCall> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(callService.cancelCall(id));
    }

    /** Indique si le média LiveKit est configuré (affichage frontend). */
    @GetMapping("/media-status")
    public ResponseEntity<Map<String, Boolean>> mediaStatus() {
        return ResponseEntity.ok(Map.of("configured", callService.isLiveKitConfigured()));
    }
}
