package com.wydad.digital.gamification.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * B.8 — Badge obtenu par un utilisateur. L'attribution est faite
 * exclusivement par le serveur (attribution automatique sur palier de
 * points) ; aucune route ne permet à un utilisateur de s'attribuer un badge.
 */
@Entity
@Table(name = "user_badges", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBadge {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private BadgeDefinition badge;

    @CreationTimestamp
    private LocalDateTime awardedAt;
}
