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
 * B.8.c — Progression d'un utilisateur sur un défi.
 *
 * Une ligne par couple (userId, challengeId). Le compteur {@code progress} est
 * incrémenté à chaque événement métier (soumission de pronostic, résolution de
 * match). Quand progress >= target, status passe à COMPLETED et la récompense
 * est créditée — une seule fois (cf. {@link com.wydad.digital.gamification.service.ChallengeService}).
 */
@Entity
@Table(name = "user_challenges",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_challenge", columnNames = {"user_id", "challenge_id"})
        },
        indexes = {
                @Index(name = "idx_uc_user", columnList = "user_id"),
                @Index(name = "idx_uc_challenge", columnList = "challenge_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserChallenge {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(nullable = false)
    private Integer progress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
