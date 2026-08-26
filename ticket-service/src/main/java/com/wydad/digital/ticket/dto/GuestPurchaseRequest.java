package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.TicketCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * B.28 — Achat sans compte (visiteur).
 * Le visiteur n'a pas de JWT : il renseigne nom, email et téléphone.
 * ticket-service crée un user VISITEUR à la volée côté auth-service
 * (rôle VISITEUR, statut VALIDE, mdp généré) puis lui rattache le billet.
 * Le visiteur reçoit le billet par email et peut, plus tard, réclamer
 * son compte via "mot de passe oublié".
 */
@Data
public class GuestPurchaseRequest {
    @NotNull private Long eventId;
    @NotNull private TicketCategory category;
    @Positive private Integer quantity;

    @NotBlank private String guestFirstName;
    @NotBlank private String guestLastName;
    @NotBlank @Email private String guestEmail;
    @NotBlank private String guestPhone;

    /** ECASH ou CARD (simulé). Le visiteur n'a pas de compte e-cash. */
    private String paymentMethod = "CARD";
}
