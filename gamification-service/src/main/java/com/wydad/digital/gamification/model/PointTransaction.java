package com.wydad.digital.gamification.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Trace d'un gain/perte de points. Chaque appel à addPoints() crée une
 * ligne ici en plus de mettre à jour le solde agrégé dans user_points.
 *
 * <p>Cette table alimente l'historique affiché dans l'espace fan
 * (« Mon historique de points ») et permet à l'admin de comprendre
 * d'où viennent les points d'un supporter (achat, pronostic, badge, …).</p>
 *
 * <p>Source = libellé stable côté serveur (PREDICTION, EXACT_SCORE,
 * BUY, BADGE, ADMIN_BONUS). refId pointe optionnellement vers la
 * source métier (matchId, orderId, badgeId…).</p>
 */
@Entity
@Table(name = "point_transactions",
        indexes = {
                @Index(name = "idx_ptx_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_ptx_source", columnList = "source")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointTransaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Positif = gain, négatif = débit. */
    @Column(nullable = false)
    private Integer amount;

    /** Code source stable côté serveur. */
    @Column(nullable = false, length = 40)
    private String source;

    /** Référence métier optionnelle (matchId, orderId, badgeId…). */
    @Column(name = "ref_id")
    private Long refId;

    /** Libellé humain court (ex. « Pronostic WAC vs IRT »). */
    @Column(length = 200)
    private String label;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
