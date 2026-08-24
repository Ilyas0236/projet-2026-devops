package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Palmarès du club : un trophée gagné (ou compteur de titres). Géré
 * exclusivement par l'ADMIN ; lecture publique pour la page « Palmarès ».
 */
@Entity
@Table(name = "trophies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trophy {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ex : "Ligue des Champions CAF", "Botola Pro". */
    @Column(nullable = false, length = 150)
    private String title;

    /** Catégorie libre gérée par le club : ex "FOOTBALL", "BASKETBALL", "INTERNATIONAL". */
    @Column(nullable = false, length = 50)
    private String category;

    /** Saison du titre, ex "2022-2023". */
    @Column(nullable = false, length = 20)
    private String season;

    /** Nombre de fois remporté (compteur de titres), minimum 1. */
    @Builder.Default
    private Integer count = 1;

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
