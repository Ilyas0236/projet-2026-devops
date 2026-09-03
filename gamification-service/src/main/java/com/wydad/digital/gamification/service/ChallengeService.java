package com.wydad.digital.gamification.service;

import com.wydad.digital.gamification.model.Challenge;
import com.wydad.digital.gamification.model.ChallengeType;
import com.wydad.digital.gamification.model.UserChallenge;
import com.wydad.digital.gamification.model.UserPoints;
import com.wydad.digital.gamification.repository.ChallengeRepository;
import com.wydad.digital.gamification.repository.PredictionRepository;
import com.wydad.digital.gamification.repository.UserChallengeRepository;
import com.wydad.digital.gamification.repository.UserPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * B.8.c — Évaluation des défis temporels.
 *
 * L'évaluation se fait à la demande sur événement métier :
 * <ul>
 *   <li>{@link #onPredictionSubmitted(Long)} après chaque pronostic enregistré,</li>
 *   <li>{@link #onPredictionsResolved(Long, int)} après la résolution d'un match,</li>
 *   <li>{@link #evaluateAll()} à la demande (admin / cron) pour rattrapper les défis
 *       de type REACH_POINTS dont la fenêtre vient de se clore.</li>
 * </ul>
 *
 * Une récompense n'est créditée qu'une seule fois (idempotence via status=COMPLETED).
 * Pour éviter la dépendance circulaire avec GamificationService (qui nous appelle pour
 * les hooks métier), on publie un événement applicatif qu'un listener transforme en
 * crédit de points.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final PredictionRepository predictionRepository;
    private final UserPointsRepository userPointsRepository;
    private final ApplicationEventPublisher events;

    // ---------- Hooks métier (appelés par GamificationService) ----------

    /** Incrémente la progression des défis SUBMIT_PREDICTIONS pour cet utilisateur. */
    @Transactional
    public void onPredictionSubmitted(Long userId) {
        evaluateForUserAndType(userId, ChallengeType.SUBMIT_PREDICTIONS, null);
    }

    /** Incrémente la progression des défis WIN_EXACT_SCORES (1 par score exact validé). */
    @Transactional
    public void onPredictionsResolved(Long userId, int countExactScores) {
        if (countExactScores <= 0) return;
        evaluateForUserAndType(userId, ChallengeType.WIN_EXACT_SCORES, countExactScores);
    }

    /** Évalue tous les défis ouverts — typiquement déclenché par un cron / l'admin. */
    @Transactional
    public int evaluateAll() {
        LocalDateTime now = LocalDateTime.now();
        List<Challenge> open = challengeRepository.findByActiveTrueAndStartsAtBeforeAndEndsAtAfter(now, now);
        int touched = 0;
        for (Challenge c : open) {
            touched += evaluateOne(c);
        }
        return touched;
    }

    // ---------- API lecture ----------

    @Transactional(readOnly = true)
    public List<Challenge> getAllChallenges() {
        return challengeRepository.findAllByOrderByStartsAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Challenge> getActiveChallenges() {
        return challengeRepository.findByActiveTrueOrderByStartsAtDesc();
    }

    /**
     * Vue "espace fan" : défi + progression de l'utilisateur connecté.
     * Si l'utilisateur n'a pas encore de ligne, on en crée une à 0.
     */
    @Transactional
    public List<ChallengeProgressDto> getChallengesForUser(Long userId) {
        List<Challenge> active = getActiveChallenges();
        for (Challenge c : active) {
            userChallengeRepository.findByUserIdAndChallengeId(userId, c.getId())
                    .orElseGet(() -> userChallengeRepository.save(UserChallenge.builder()
                            .userId(userId)
                            .challengeId(c.getId())
                            .progress(0)
                            .status(UserChallenge.Status.IN_PROGRESS)
                            .build()));
        }
        List<Long> ids = active.stream().map(Challenge::getId).toList();
        List<UserChallenge> progresses = userChallengeRepository.findByUserIdAndChallengeIdIn(userId, ids);
        return active.stream().map(c -> {
            UserChallenge p = progresses.stream()
                    .filter(x -> x.getChallengeId().equals(c.getId()))
                    .findFirst()
                    .orElse(null);
            return new ChallengeProgressDto(c, p);
        }).toList();
    }

    // ---------- CRUD admin ----------

    @Transactional
    public Challenge create(Challenge c) {
        validate(c);
        c.setId(null);
        c.setCreatedAt(null);
        return challengeRepository.save(c);
    }

    @Transactional
    public Challenge update(Long id, Challenge patch) {
        Challenge c = challengeRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Défi non trouvé: " + id));
        if (patch.getTitle() != null && !patch.getTitle().isBlank()) c.setTitle(patch.getTitle().trim());
        if (patch.getDescription() != null) c.setDescription(patch.getDescription().isBlank() ? null : patch.getDescription().trim());
        if (patch.getTarget() != null && patch.getTarget() > 0) c.setTarget(patch.getTarget());
        if (patch.getRewardPoints() != null && patch.getRewardPoints() > 0) c.setRewardPoints(patch.getRewardPoints());
        if (patch.getStartsAt() != null) c.setStartsAt(patch.getStartsAt());
        if (patch.getEndsAt() != null) c.setEndsAt(patch.getEndsAt());
        if (patch.getChallengeType() != null) c.setChallengeType(patch.getChallengeType());
        c.setActive(patch.isActive());
        validate(c);
        return challengeRepository.save(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!challengeRepository.existsById(id)) {
            throw new jakarta.persistence.EntityNotFoundException("Défi non trouvé: " + id);
        }
        userChallengeRepository.findByChallengeId(id).forEach(userChallengeRepository::delete);
        challengeRepository.deleteById(id);
    }

    // ---------- Helpers ----------

    private void evaluateForUserAndType(Long userId, ChallengeType type, Integer increment) {
        LocalDateTime now = LocalDateTime.now();
        List<Challenge> candidates = challengeRepository
                .findByActiveTrueAndStartsAtBeforeAndEndsAtAfter(now, now)
                .stream()
                .filter(c -> c.getChallengeType() == type)
                .toList();
        for (Challenge c : candidates) {
            UserChallenge p = userChallengeRepository
                    .findByUserIdAndChallengeId(userId, c.getId())
                    .orElseGet(() -> userChallengeRepository.save(UserChallenge.builder()
                            .userId(userId)
                            .challengeId(c.getId())
                            .progress(0)
                            .status(UserChallenge.Status.IN_PROGRESS)
                            .build()));
            if (p.getStatus() == UserChallenge.Status.COMPLETED) continue;
            int delta = (increment != null) ? increment : 1;
            p.setProgress(p.getProgress() + delta);
            if (p.getProgress() >= c.getTarget()) {
                p.setStatus(UserChallenge.Status.COMPLETED);
                p.setCompletedAt(LocalDateTime.now());
                // Créditer la récompense via événement applicatif (évite la
                // dépendance circulaire ChallengeService ↔ GamificationService).
                events.publishEvent(new ChallengeCompletedEvent(
                        userId, c.getId(), c.getRewardPoints(), c.getTitle()));
                log.info("B.8.c userId={} challenge={} complétée → +{} pts", userId, c.getId(), c.getRewardPoints());
            }
            userChallengeRepository.save(p);
        }
    }

    /** Évalue les défis REACH_POINTS pour tous les utilisateurs (boucle bornée). */
    private int evaluateOne(Challenge c) {
        if (c.getChallengeType() != ChallengeType.REACH_POINTS) {
            // SUBMIT_PREDICTIONS / WIN_EXACT_SCORES sont incrémentaux, déjà gérés.
            return 0;
        }
        int touched = 0;
        for (UserPoints up : userPointsRepository.findAll()) {
            UserChallenge p = userChallengeRepository
                    .findByUserIdAndChallengeId(up.getUserId(), c.getId())
                    .orElseGet(() -> userChallengeRepository.save(UserChallenge.builder()
                            .userId(up.getUserId())
                            .challengeId(c.getId())
                            .progress(0)
                            .status(UserChallenge.Status.IN_PROGRESS)
                            .build()));
            if (p.getStatus() == UserChallenge.Status.COMPLETED) continue;
            // Snapshot du solde actuel : si >= target, on complète
            p.setProgress(up.getTotalPoints());
            if (up.getTotalPoints() >= c.getTarget()) {
                p.setStatus(UserChallenge.Status.COMPLETED);
                p.setCompletedAt(LocalDateTime.now());
                events.publishEvent(new ChallengeCompletedEvent(
                        up.getUserId(), c.getId(), c.getRewardPoints(), c.getTitle()));
                touched++;
            }
            userChallengeRepository.save(p);
        }
        return touched;
    }

    private void validate(Challenge c) {
        if (c.getTitle() == null || c.getTitle().isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }
        if (c.getTarget() == null || c.getTarget() <= 0) {
            throw new IllegalArgumentException("L'objectif doit être strictement positif");
        }
        if (c.getRewardPoints() == null || c.getRewardPoints() <= 0) {
            throw new IllegalArgumentException("La récompense doit être strictement positive");
        }
        if (c.getStartsAt() == null || c.getEndsAt() == null || !c.getEndsAt().isAfter(c.getStartsAt())) {
            throw new IllegalArgumentException("La fenêtre de temps est invalide (endsAt doit être après startsAt)");
        }
    }

    /** DTO exposé à l'API pour la vue "espace fan". */
    public record ChallengeProgressDto(Challenge challenge, UserChallenge progress) {}

    /**
     * Événement publié quand un défi vient d'être complété. Écouté par
     * {@link com.wydad.digital.gamification.service.ChallengeRewardListener}
     * qui crédite la récompense — évite la dépendance circulaire.
     */
    public record ChallengeCompletedEvent(Long userId, Long challengeId, int rewardPoints, String title) {}
}
