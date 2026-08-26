package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Convocation d'un joueur pour un MATCH (§8 du cahier des charges), à
 * distinguer de la convocation de séance ({@link Convocation}).
 *
 * <p>Le match vit dans le content-service : il est référencé ici par son
 * identifiant, avec discipline + catégorie dénormalisées depuis la fiche
 * match (§26 — jamais mélangées entre groupes). Le staff entraîneur du
 * groupe prépare la liste (titulaires / remplaçants), l'ADMIN la reçoit,
 * la consulte et la PUBLIE sur le site public (§9).</p>
 */
@Entity
@Table(name = "match_convocations",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"match_id", "joueur_user_id"},
                name = "uk_match_convocation_player"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchConvocation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant du match côté content-service. */
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    /** Discipline + catégorie DU MATCH — font foi, jamais falsifiables. */
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private SportType sportType;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private Long joueurUserId;

    /** Statut dans la feuille de match. */
    public enum PlayerRole { TITULAIRE, REMPLACANT }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayerRole playerRole;

    /**
     * Cycle de vie §8/§9 : DRAFT (préparée par l'entraîneur) → SOUMISE
     * (envoyée à l'Admin) → PUBLIEE (visible site public) → ou REFUSEE.
     * La publication est une décision ADMIN, jamais entraîneur.
     */
    public enum PublicationStatus { DRAFT, SOUMISE, PUBLIEE, REFUSEE }

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PublicationStatus status;

    /** Entraîneur ayant convoqué (userId auth-service ; 0 si ADMIN). */
    @Column(nullable = false)
    private Long createdByStaffUserId;

    /** Motif d'un refus ADMIN éventuel. */
    @Column(length = 500)
    private String rejectionReason;

    private LocalDateTime submittedAt;
    private LocalDateTime publishedAt;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
