package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.SeatType;
import com.wydad.digital.ticket.enums.TicketCategory;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Patch partiel d'une section de billetterie (ADMIN).
 *
 * <p>Conçu pour corriger le prix (et éventuellement la capacité) d'une section
 * existante SANS toucher à la table {@code tickets} (un PUT qui supprime puis
 * recrée la section viole la FK dès qu'un billet a été vendu). Tous les champs
 * sont optionnels : seul ce qui est non-null est appliqué.</p>
 *
 * <p>Validations :</p>
 * <ul>
 *   <li>{@code price} > 0 si fourni</li>
 *   <li>{@code capacity} ≥ 0 si fourni (mais on refuse de baisser la capacité
 *       en dessous du nombre de billets déjà vendus, vérifié en service)</li>
 *   <li>{@code name} non vide si fourni</li>
 * </ul>
 */
@Data
public class SectionPatchRequest {
    private String name;
    private TicketCategory category;
    private SeatType seatType;
    private Integer capacity;
    private BigDecimal price;
}
