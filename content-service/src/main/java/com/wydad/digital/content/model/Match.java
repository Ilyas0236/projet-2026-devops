package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime heure;

    @Column(nullable = false)
    private String adversaire;

    private String adversaireLogoUrl;

    @Column(nullable = false)
    private String competition;

    @Column(nullable = false)
    private String lieu;

    private Integer scoreWydad;

    private Integer scoreAdversaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatut statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportSection sport;

    /** Catégorie d'âge (§26). Nullable pour les matchs créés avant la mise en place. */
    @Enumerated(EnumType.STRING)
    private MatchCategory categorie;

    @CreationTimestamp
    private java.time.LocalDateTime createdAt;
}