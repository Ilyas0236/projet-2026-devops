package com.wydad.digital.ticket.model;

import com.wydad.digital.ticket.enums.EventStatus;
import com.wydad.digital.ticket.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EventStatus status = EventStatus.UPCOMING;

    private String homeTeam;
    private String awayTeam;
    private String venue;
    private String competition;

    /**
     * §26 — catégorie du groupe concerné par le match (SENIOR, U20…).
     * Nullable pour les événements créés avant la mise en place (comportement
     * historique : billets pour tous les joueurs actifs). Dès qu'elle est
     * renseignée, seuls les joueurs de la discipline+catégorie du match
     * reçoivent des billets (§24 : jamais un joueur Football U17 sur un
     * match Basketball U17).
     */
    @Enumerated(EnumType.STRING)
    private EventCategory category;

    /** §16/§21 — logo adverse (image téléversée par l'ADMIN), repris sur le PDF. */
    @Column(length = 2000)
    private String adversaireLogoUrl;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    private LocalDateTime gateOpenTime;

    /**
     * B.12 — Match EXCEPTIONNEL (LDC/quart/semi/finale). Si true, les
     * ADHÉRENTS (abonnement saisonnier actif) bénéficient d'une fenêtre
     * d'achat prioritaire de 48h avant l'ouverture au public.
     *
     * Stocké en base mais nullable : les événements historiques ne sont
     * pas concernés. Migration ALTER TABLE manuelle en prod.
     */
    @Builder.Default
    private Boolean exceptional = false;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    private Integer totalCapacity;
    @Builder.Default
    private Integer availableSeats = 0;
    @Builder.Default
    private Integer soldTickets = 0;

    private String posterUrl;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Section> sections = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
