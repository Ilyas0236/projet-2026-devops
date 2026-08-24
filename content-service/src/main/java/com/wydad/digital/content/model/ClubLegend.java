package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Légende du club (Hall of Fame) : joueur emblématique ayant marqué
 * l'histoire du Wydac. Géré exclusivement par l'ADMIN ; lecture publique
 * pour la page « Légendes ».
 */
@Entity
@Table(name = "club_legends")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClubLegend {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Surnom emblématique, ex "Le Roi". */
    @Column(length = 80)
    private String nickname;

    /** Poste ou discipline, ex "Attaquant", "Handball". */
    @Column(nullable = false, length = 60)
    private String role;

    /** Première année au club (ex 1958). */
    @Column(nullable = false)
    private Integer yearFrom;

    /** Dernière année au club ; null = encore au club / inconnue. */
    private Integer yearTo;

    /** Biographie courte affichée publiquement. */
    @Column(length = 1000)
    private String biography;

    @Column(length = 500)
    private String imageUrl;

    /** Ordre d'affichage public (croissant). */
    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
