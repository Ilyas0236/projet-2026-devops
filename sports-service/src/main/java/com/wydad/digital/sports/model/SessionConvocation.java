package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Convocation d'un joueur pour une séance d'entraînement.
 *
 * <p>Une ligne par couple (séance, joueur). Différent de
 * {@link Convocation} (workflow de réponse joueur CONFIRME/ABSENT/RETARD —
 * B.3.a) : ici on représente le choix du staff entraîneur, et l'ADMIN
 * consulte la liste.</p>
 *
 * <p>Séances et convocations vivent dans le même service, donc on référence
 * {@code sessionId} par Long pour découpler le chargement (la séance est
 * remontée à la demande pour l'affichage).</p>
 */
@Entity
@Table(name = "session_convocations",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"session_id", "joueur_user_id"},
                name = "uk_session_convocation_player"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SessionConvocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant de la séance (sports-service). */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** Discipline + catégorie DE LA SÉANCE — font foi, jamais falsifiables. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportType sportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "joueur_user_id", nullable = false)
    private Long joueurUserId;

    /**
     * Cycle de vie minimal : DRAFT (créé par l'entraîneur) → CONVOQUE
     * (notification in-app envoyée). L'ADMIN consulte en lecture seule.
     */
    public enum Status { DRAFT, CONVOQUE }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Entraîneur ayant convoqué (userId auth-service ; 0 si ADMIN). */
    @Column(name = "created_by_staff_user_id", nullable = false)
    private Long createdByStaffUserId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
