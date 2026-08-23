package com.wydad.digital.gamification.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * B.8 — Définition d'un badge de fidélité, gérée par l'ADMIN.
 * Un badge est attribué automatiquement dès que le solde de points de
 * l'utilisateur atteint minPoints (jamais manuellement).
 */
@Entity
@Table(name = "badge_definitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BadgeDefinition {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code stable (ex "PREMIER_PRONOSTIC", "VETERAN_2000"). */
    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    /** Seuil de points cumulés déclenchant l'attribution automatique. */
    @Column(nullable = false)
    private Integer minPoints;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
