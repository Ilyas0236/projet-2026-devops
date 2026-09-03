package com.wydad.digital.gamification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * B.8.c — Défi temporel piloté par l'ADMIN.
 *
 * Un défi a un type d'objectif (SUBMIT_PREDICTIONS, WIN_EXACT_SCORES, REACH_POINTS),
 * une cible numérique (target) et une fenêtre temporelle (startsAt, endsAt).
 * L'évaluation est faite à la demande (par l'admin qui appelle
 * /api/gamification/admin/challenges/{id}/evaluate) et à chaque fin de match
 * via {@link com.wydad.digital.gamification.service.ChallengeService.evaluateOpenChallenges()}.
 *
 * Les récompenses sont attribuées via {@link com.wydad.digital.gamification.service.GamificationService#addPoints}
 * ce qui alimente automatiquement l'historique (point_transactions) et déclenche
 * l'attribution des badges franchis (B.8).
 */
@Entity
@Table(name = "challenges",
        indexes = {
                @Index(name = "idx_chg_window", columnList = "starts_at, ends_at"),
                @Index(name = "idx_chg_type", columnList = "challenge_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Libellé public (ex: "Pronostiqueur du mois — septembre"). */
    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    /** Type d'objectif — piloté par l'enum ChallengeType. */
    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false, length = 40)
    private ChallengeType challengeType;

    /** Cible à atteindre (ex: 10 pronostics, 3 scores exacts, 200 points). */
    @Column(nullable = false)
    private Integer target;

    /** Récompense en points à verser quand l'objectif est atteint. */
    @Column(name = "reward_points", nullable = false)
    private Integer rewardPoints;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
