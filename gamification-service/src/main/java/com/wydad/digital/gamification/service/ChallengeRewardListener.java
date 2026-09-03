package com.wydad.digital.gamification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * B.8.c — Listener qui crédite la récompense d'un défi quand celui-ci vient
 * d'être marqué COMPLETED par {@link ChallengeService}.
 *
 * <p>Indirection nécessaire pour casser la dépendance circulaire
 * ChallengeService ↔ GamificationService : on publie un événement applicatif
 * et on écoute ici dans une transaction indépendante (REQUIRES_NEW) pour
 * garantir que le crédit passe même si la transaction parente est en
 * readOnly ou en rollback.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChallengeRewardListener {

    private final GamificationService gamificationService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChallengeCompleted(ChallengeService.ChallengeCompletedEvent event) {
        try {
            gamificationService.addPoints(
                    event.userId(),
                    event.rewardPoints(),
                    "CHALLENGE",
                    event.challengeId(),
                    "Défi validé : " + event.title());
            log.info("B.8.c reward credited userId={} challenge={} +{} pts",
                    event.userId(), event.challengeId(), event.rewardPoints());
        } catch (RuntimeException e) {
            // On ne veut pas faire échouer la transaction parente (le statut
            // COMPLETED est déjà persisté) — on logue et un admin pourra
            // rejouer l'évaluation via /admin/challenges/evaluate.
            log.error("B.8.c reward credit failed userId={} challenge={}",
                    event.userId(), event.challengeId(), e);
        }
    }
}
