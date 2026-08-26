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
    private BigDecimal basePrice;
    private Integer totalCapacity;
    private Integer availableSeats;
    private Integer soldTickets;
    private String posterUrl;
    /** §16/§21 — logo adverse téléversé par l'ADMIN. */
    private String adversaireLogoUrl;
    private List<SectionResponse> sections;
    private LocalDateTime createdAt;
}
