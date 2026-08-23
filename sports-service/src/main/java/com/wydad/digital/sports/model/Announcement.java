package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Annonce du club ou du staff (B.5) : ciblée soit sur une catégorie/sport
 * précis, soit sur tout le club (sportType/category nuls). La visibilité
 * est filtrée côté serveur selon la catégorie de l'utilisateur.
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
    @Enumerated(EnumType.STRING) private SportType sportType;
    @Enumerated(EnumType.STRING) private Category category;

    @Column(nullable = false)
    private Long createdByStaffId;

    @Column(nullable = false)
    private String createdByName;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
