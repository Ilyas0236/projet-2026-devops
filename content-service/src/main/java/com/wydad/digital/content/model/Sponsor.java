package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * B.7 — Sponsor / partenaire du club. Géré exclusivement par l'ADMIN ;
 * lecture publique pour les pages vitrine et espace membre.
 */
@Entity
@Table(name = "sponsors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Sponsor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String logoUrl;

    @Column(length = 300)
    private String websiteUrl;

    /**
     * Niveau de partenariat (libellé libre géré par le club) :
     * ex "MAIN_SPONSOR", "OFFICIAL_PARTNER", "SUPPLIER".
     */
    @Column(nullable = false, length = 50)
    private String tier;

    /** Ordre d'affichage public (croissant). */
    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
