package com.wydad.digital.sports.controller;

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
 * Création : ENTRAINEUR / PRESIDENT / ADMIN / JOUEUR (re-vérifié dans le
 * service ; le joueur est borné aux coéquipiers de son groupe).
 * Lecture/jeton : organisateur ou participant (liste fermée).
 * Sorties via CallView : jamais l'entité brute (participantUserIds/roomName
 * ne fuient pas aux clients).
 */
@RestController
@RequestMapping("/api/sports/calls")
@RequiredArgsConstructor
public class ScheduledCallController {

    private final ScheduledCallService callService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ENTRAINEUR','PRESIDENT','ADMIN','JOUEUR')")
    public ResponseEntity<ScheduledCallService.CallView> create(@RequestBody ScheduledCallService.CreateCallRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ScheduledCallService.CallView.from(callService.createCall(req)));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ScheduledCallService.CallView>> mine() {
        return ResponseEntity.ok(
                callService.getMyCalls().stream().map(ScheduledCallService.CallView::from).toList());
    }

    /** Jeton de connexion média — organisateur ou participant uniquement. */
    @PostMapping("/{id}/token")
    public ResponseEntity<ScheduledCallService.CallToken> token(@PathVariable Long id) {
        return ResponseEntity.ok(callService.joinToken(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','PRESIDENT','ADMIN','JOUEUR')")
    public ResponseEntity<ScheduledCallService.CallView> cancel(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ScheduledCallService.CallView.from(callService.cancelCall(id)));
    }

    /** Indique si le média LiveKit est configuré (affichage frontend). */
    @GetMapping("/media-status")
    public ResponseEntity<Map<String, Boolean>> mediaStatus() {
        return ResponseEntity.ok(Map.of("configured", callService.isLiveKitConfigured()));
    }
}
