package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Statistique de match d'un joueur (B.4) : une ligne par rencontre,
 * saisie par le staff encadrant la catégorie du joueur. Les totaux de
 * la fiche (matchesPlayed/goals/assists) sont AGRÉGÉS depuis ces lignes
 * réelles — plus aucune statistique figée sans source métier.
 */
@Entity
@Table(name = "match_stats")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchStat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long joueurUserId;

    /** Catégorie/sport du joueur au moment de la saisie (dénormalisés). */
    @Enumerated(EnumType.STRING) private SportType sportType;
    @Enumerated(EnumType.STRING) private Category category;

    @Column(nullable = false)
    private String opponent;

    @Column(nullable = false)
    private LocalDate matchDate;

    @Builder.Default
    private int goals = 0;

    @Builder.Default
    private int assists = 0;

    /** Minutes jouées (optionnel). */
    private Integer minutesPlayed;

    /** Compétition librement libellée (ex : Championnat U19). */
    @Column(length = 120)
    private String competition;

    @Column(nullable = false)
    private Long createdByStaffId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
