package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.EventType;
import com.wydad.digital.ticket.model.EventCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateEventRequest {
    @NotBlank private String title;
    private String description;
    @NotNull private EventType eventType;
    /** §26 — catégorie du groupe (SENIOR = équipe A). */
    private EventCategory category;
    @NotBlank private String homeTeam;
    private String awayTeam;
    @NotBlank private String venue;
    private String competition;
    /** §16/§21 — logo adverse (image téléversée par l'ADMIN), repris sur le PDF. */
    private String adversaireLogoUrl;
    @NotNull private LocalDateTime eventDate;
    private LocalDateTime gateOpenTime;
    @NotNull @Positive private BigDecimal basePrice;
    @NotNull @Positive private Integer totalCapacity;
    private String posterUrl;
    private List<SectionRequest> sections;
    /**
     * B.12 — Match EXCEPTIONNEL (LDC/quart/semi/finale). Active la fenêtre
     * d'achat prioritaire de 48h pour les ADHÉRENTS. Optionnel : sur un
     * PUT, l'omission conserve la valeur actuelle (un événement reste
     * "non-exceptionnel" par défaut).
     */
    private Boolean exceptional;
    /**
     * V1.1 — Référence logique (id) au match de calendrier dans
     * content-service. Nullable. L'admin peut soit créer un événement
     * "indépendant" (cérémonie, gala), soit l'adosser à un match existant :
     * dans ce second cas, le front pré-remplit titre/date/lieu/adversaire
     * depuis {@code GET /api/content/matches}.
     */
    private Long matchId;
}
