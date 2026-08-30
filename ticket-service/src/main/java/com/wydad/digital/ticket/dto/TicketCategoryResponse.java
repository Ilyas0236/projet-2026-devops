package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.TicketCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Élément de la grille tarifaire pilotée par le back :
 * alimente le <select> "Catégorie" du formulaire admin
 * (section de billetterie) ainsi que la home publique.
 *
 * <p>Le {@code defaultPrice} est une <strong>indication</strong> :
 * l'admin peut l'écraser section par section. Le prix réel vendu
 * reste stocké sur {@code sections.price}.</p>
 */
@Data @Builder
public class TicketCategoryResponse {
    private String code;
    private String label;
    private BigDecimal defaultPrice;

    public static TicketCategoryResponse from(TicketCategory cat) {
        return TicketCategoryResponse.builder()
                .code(cat.name())
                .label(cat.getLabel())
                .defaultPrice(cat.getDefaultPrice())
                .build();
    }
}
