package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.EventStatus;
import com.wydad.digital.ticket.enums.EventType;
import com.wydad.digital.ticket.model.EventCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private EventType eventType;
    /** §26 — catégorie du groupe (SENIOR, U20…). */
    private EventCategory category;
    private EventStatus status;
    private String homeTeam;
    private String awayTeam;
    private String venue;
    private String competition;
    private LocalDateTime eventDate;
    private LocalDateTime gateOpenTime;
    /**
     * B.12 — Si true, les ADHÉRENTS (abonnement saisonnier actif) ont une
     * fenêtre d'achat prioritaire de 48h avant l'ouverture au public.
     * Le front affiche un bandeau d'information, et l'achat par un
     * utilisateur non-adhérent est refusé tant que cette fenêtre court.
     */
    private Boolean exceptional;
    private BigDecimal basePrice;
    private Integer totalCapacity;
    private Integer availableSeats;
    private Integer soldTickets;
    private String posterUrl;
    /** §16/§21 — logo adverse téléversé par l'ADMIN. */
    private String adversaireLogoUrl;
    private List<SectionResponse> sections;
    private LocalDateTime createdAt;
    /** V1.1 — id du match de calendrier (content-service) adossé à cet événement. */
    private Long matchId;
}
