package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.model.PointTransaction;
import com.wydad.digital.gamification.model.UserBadge;
import com.wydad.digital.gamification.service.BadgeService;
import com.wydad.digital.gamification.service.GamificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pilotage admin du système de fidélité.
 *
 * <ul>
 *   <li>{@code /admin/badges/recent} — attributions de badges récentes (tous
 *       utilisateurs), pour que l'admin suive le déblocage en temps réel ;</li>
 *   <li>{@code /admin/points/recent} — flux global des derniers gains/pertes
 *       de points, pour l'audit (qui a farmé, d'où viennent les points…).</li>
 * </ul>
 *
 * ADMIN uniquement. Le contrôleur est séparé de {@link BadgeController}
 * pour distinguer « gestion du catalogue » (CRUD) de « pilotage opérationnel »
 * (consultation des attributions).
 */
@RestController
@RequestMapping("/api/gamification/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminGamificationController {

    private final BadgeService badgeService;
    private final GamificationService gamificationService;

    /** Attributions de badges les plus récentes (tous utilisateurs). */
    @GetMapping("/badges/recent")
    public ResponseEntity<List<AdminBadgeAwardDto>> recentBadgeAwards(
            @RequestParam(defaultValue = "50") int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        List<UserBadge> awards = badgeService.getRecentAwards(safe);
        return ResponseEntity.ok(awards.stream().map(AdminBadgeAwardDto::of).toList());
    }

    /** Flux global des derniers gains de points. */
    @GetMapping("/points/recent")
    public ResponseEntity<List<PointTransaction>> recentPointTransactions(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(gamificationService.getRecentPointTransactions(limit));
    }

    /** DTO léger pour l'écran d'attribution (renvoie juste ce qu'il faut). */
    public record AdminBadgeAwardDto(Long id, Long userId, String badgeCode,
                                     String badgeName, Integer minPoints,
                                     java.time.LocalDateTime awardedAt) {
        public static AdminBadgeAwardDto of(UserBadge ub) {
            return new AdminBadgeAwardDto(
                    ub.getId(),
                    ub.getUserId(),
                    ub.getBadge().getCode(),
                    ub.getBadge().getName(),
                    ub.getBadge().getMinPoints(),
                    ub.getAwardedAt());
        }
    }
}
