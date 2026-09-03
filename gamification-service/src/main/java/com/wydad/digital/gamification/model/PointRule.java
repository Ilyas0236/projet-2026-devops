package com.wydad.digital.gamification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * B.8 — Barème de points paramétrable par l'admin.
 *
 * <p>Table clé/valeur (key = nom métier en kebab-case, value = entier).
 * Le seed par défaut est posé au boot si la table est vide.</p>
 *
 * <p>Clés reconnues :</p>
 * <ul>
 *   <li>{@code prediction.bonus} : points crédités pour avoir soumis un pronostic (10)</li>
 *   <li>{@code prediction.exact_score} : points crédités pour un score exact (25)</li>
 *   <li>{@code prediction.bon_signe} : points crédités pour un bon signe 1X2 (10)</li>
 *   <li>{@code level.points_per_level} : points cumulés pour passer au niveau suivant (500)</li>
 * </ul>
 */
@Entity
@Table(name = "point_rules", indexes = {
        @Index(name = "idx_point_rules_key", columnList = "rule_key", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom métier unique (kebab-case). */
    @Column(name = "rule_key", nullable = false, length = 64, unique = true)
    private String ruleKey;

    /** Libellé humain affiché côté admin. */
    @Column(nullable = false, length = 200)
    private String label;

    /** Description optionnelle affichée en infobulle. */
    @Column(length = 500)
    private String description;

    /** Valeur entière (peut être négative pour des règles de pénalité futures). */
    @Column(nullable = false)
    private Integer value;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
