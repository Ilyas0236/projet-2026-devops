package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Phase 5 — appel vidéo/vocal programmé par l'entraîneur (sa catégorie),
 * le président (joueurs/premium/staff) ou l'admin. Les participants sont
 * stockés explicitement (liste fermée) : un utilisateur absent de la liste
 * ne peut ni rejoindre ni obtenir de jeton LiveKit pour la room.
 */
@Entity
@Table(name = "scheduled_calls")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduledCall {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    /** Nom de room LiveKit, unique, généré côté serveur (jamais fourni par le client). */
    @Column(nullable = false, unique = true, length = 80)
    private String roomName;

    @Enumerated(EnumType.STRING) private SportType sportType;
    @Enumerated(EnumType.STRING) private Category category;

    @Column(nullable = false)
    private Long organizerUserId;

    @Column(nullable = false, length = 100)
    private String organizerName;

    /** Rôle JWT de l'organisateur (ENTRAINEUR/PRESIDENT/ADMIN). */
    @Column(nullable = false, length = 20)
    private String organizerRole;

    private LocalDateTime scheduledAt;

    /** Durée indicative (minutes) — purement informatif. */
    private Integer durationMinutes;

    /** Cycle de vie : PROGRAMME → EN_COURS → TERMINE (ou ANNULE). */
    public enum CallStatus { PROGRAMME, EN_COURS, TERMINE, ANNULE }

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CallStatus status = CallStatus.PROGRAMME;

    /** Participants autorisés (liste fermée). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scheduled_call_participants", joinColumns = @JoinColumn(name = "call_id"))
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private Set<Long> participantUserIds = new HashSet<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
