package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.filter.UserContext;
import com.wydad.digital.gamification.service.ChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * B.8.c — Vue "défis en cours" pour l'espace supporter.
 *
 * Renvoie la liste des défis actifs avec la progression de l'utilisateur
 * courant (créée à 0 au premier appel si besoin). Accessible à tout
 * utilisateur authentifié — un visiteur ne verra rien (cf. SecurityConfig).
 */
@RestController
@RequestMapping("/api/gamification/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @GetMapping("/me")
    public ResponseEntity<List<ChallengeService.ChallengeProgressDto>> myChallenges() {
        Long uid = UserContext.getCurrentUserId();
        if (uid == null) throw new AccessDeniedException("Utilisateur non authentifié");
        return ResponseEntity.ok(challengeService.getChallengesForUser(uid));
    }
}
