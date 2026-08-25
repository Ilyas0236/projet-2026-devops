package com.wydad.digital.communication.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Annonce du club ou du staff (B.5) : ciblée soit sur une catégorie/sport
 * précis, soit sur tout le club (sportType/category nuls). La visibilité
 * est filtrée côté serveur selon la catégorie de l'utilisateur.
 *
 * Le sport/catégorie sont stockés en STRING (pas d'enum partagée avec
 * sports-service) : le découplage inter-services prime — communication-
 * service ne doit pas dépendre des classes du domaine sportif.
 */
@Entity
@Table(name = "announcements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Announcement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    /** Null = annonce club (tous), sinon réservée à ce sport/catégorie. */
    @Column(name = "sport_type", length = 20)
    private String sportType;
    @Column(length = 20)
    private String category;

    @Column(nullable = false)
    private Long createdByStaffId;

    @Column(nullable = false)
    private String createdByName;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
