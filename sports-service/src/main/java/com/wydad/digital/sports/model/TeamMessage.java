package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Phase 4 — message de GROUPE « WhatsApp » (texte uniquement) dans le
 * groupe « Équipe {sport} {catégorie} » : joueurs + staff encadrant la
 * catégorie. L'adhésion au groupe n'est pas stockée : elle est DÉDUITE du
 * sport/catégorie de la fiche (player ou staff), ce qui garantit qu'un
 * membre parti de l'équipe n'a plus accès à l'historique.
 */
@Entity
@Table(name = "team_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Groupe = sportType + category (ex. FOOTBALL U19). */
    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false, length = 20)
    private SportType sportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false, length = 100)
    private String senderName;

    /** Rôle JWT de l'expéditeur au moment de l'envoi (JOUEUR/STAFF/ADMIN…). */
    @Column(nullable = false, length = 20)
    private String senderRole;

    @Column(nullable = false, length = 500)
    private String content;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
