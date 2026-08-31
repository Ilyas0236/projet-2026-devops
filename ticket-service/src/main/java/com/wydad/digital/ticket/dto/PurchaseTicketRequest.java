package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.TicketCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * B.18 — si {@code beneficiaryAcademyMemberId} est fourni, l'achat est
 * fait pour un enfant académie rattaché au parent connecté. Le parent
 * est le payeur (E-Cash débité sur son wallet) ; le billet est créé
 * avec l'identité du fils (User shadow). Le contrôle IDOR (l'enfant
 * est-il bien rattaché à CE parent ?) est fait dans
 * {@code TicketService#purchaseTickets}.
 */
@Data
public class PurchaseTicketRequest {
    @NotNull private Long eventId;
    @NotNull private Long userId;
    private String userFullName;
    private String userEmail;
    @NotNull private TicketCategory category;
    @Positive private Integer quantity;
    private String paymentMethod = "ECASH";
    private Long beneficiaryAcademyMemberId;
}
