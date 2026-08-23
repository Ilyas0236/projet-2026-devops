package com.wydad.digital.gamification.service;

import com.wydad.digital.gamification.model.BadgeDefinition;
import com.wydad.digital.gamification.model.UserBadge;
import com.wydad.digital.gamification.model.UserPoints;
import com.wydad.digital.gamification.repository.BadgeDefinitionRepository;
import com.wydad.digital.gamification.repository.UserBadgeRepository;
import com.wydad.digital.gamification.repository.UserPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B.8 — Badges de fidélité : définitions gérées par l'ADMIN, attribution
 * AUTOMATIQUE côté serveur dès que le solde de points atteint le seuil.
 * Aucune route ne permet d'attribuer un badge manuellement : l'attribution
 * ne peut être déclenchée que par une évolution réelle du solde de points,
 * elle-même produite par des actions légitimes (pronostics, bonus ADMIN).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {

    private final BadgeDefinitionRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserPointsRepository userPointsRepository;
    private final com.wydad.digital.gamification.client.NotificationClient notificationClient;

    // ---------- Lecture ----------

    /** Badges actifs triés par seuil (affichage public / espace fan). */
    @Transactional(readOnly = true)
    public List<BadgeDefinition> getActiveBadges() {
        return badgeRepository.findByActiveTrueOrderByMinPointsAsc();
    }

    /** Tous les badges, y compris inactifs (ADMIN). */
    @Transactional(readOnly = true)
    public List<BadgeDefinition> getAllBadges() {
        return badgeRepository.findAllByOrderByMinPointsAsc();
    }

    @Transactional(readOnly = true)
    public List<UserBadge> getBadgesOfUser(Long userId) {
        return userBadgeRepository.findByUserIdOrderByAwardedAtDesc(userId);
    }

    // ---------- Administration (contrôleur @PreAuthorize ADMIN) ----------

    @Transactional
    public BadgeDefinition create(String code, String name, String description, Integer minPoints) {
        validate(code, name, minPoints);
        String normalizedCode = code.trim().toUpperCase();
        if (badgeRepository.findByCode(normalizedCode).isPresent()) {
            throw new IllegalArgumentException("Un badge avec ce code existe déjà");
        }
        return badgeRepository.save(BadgeDefinition.builder()
                .code(normalizedCode)
                .name(name.trim())
                .description(description != null && !description.isBlank() ? description.trim() : null)
                .minPoints(minPoints)
                .build());
    }

    @Transactional
    public BadgeDefinition update(Long id, String name, String description, Integer minPoints, Boolean active) {
        BadgeDefinition b = badgeRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Badge non trouvé: " + id));
        if (name != null && !name.isBlank()) b.setName(name.trim());
        if (description != null) b.setDescription(description.isBlank() ? null : description.trim());
        if (minPoints != null) {
            if (minPoints < 0) throw new IllegalArgumentException("Le seuil de points doit être positif ou nul");
            b.setMinPoints(minPoints);
        }
        if (active != null) b.setActive(active);
        return badgeRepository.save(b);
    }

    @Transactional
    public void delete(Long id) {
        if (!badgeRepository.existsById(id)) {
            throw new jakarta.persistence.EntityNotFoundException("Badge non trouvé: " + id);
        }
        badgeRepository.deleteById(id);
    }

    // ---------- Attribution automatique ----------

    /**
     * Attribue à l'utilisateur tous les badges actifs dont le seuil est
     * atteint et qu'il ne possède pas encore. Appelé après chaque mutation
     * du solde de points. Retourne les badges nouvellement attribués.
     */
    @Transactional
    public List<BadgeDefinition> awardEligibleBadges(Long userId) {
        UserPoints points = userPointsRepository.findById(userId).orElse(null);
        if (points == null) return List.of();

        List<BadgeDefinition> newlyAwarded = new java.util.ArrayList<>();
        for (BadgeDefinition badge : badgeRepository.findByActiveTrueOrderByMinPointsAsc()) {
            if (points.getTotalPoints() >= badge.getMinPoints()
                    && !userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
                userBadgeRepository.save(UserBadge.builder().userId(userId).badge(badge).build());
                newlyAwarded.add(badge);
                notificationClient.notifyUser(
                        userId,
                        null,
                        "Nouveau badge débloqué !",
                        "Félicitations, vous obtenez le badge « " + badge.getName() + " » !",
                        "/espace-fan");
            }
        }
        return newlyAwarded;
    }

    private void validate(String code, String name, Integer minPoints) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Le code du badge est obligatoire");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Le nom du badge est obligatoire");
        if (minPoints == null || minPoints < 0) throw new IllegalArgumentException("Le seuil de points doit être positif ou nul");
    }
}
