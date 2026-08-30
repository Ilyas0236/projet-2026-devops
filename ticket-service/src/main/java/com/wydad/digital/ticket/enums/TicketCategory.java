package com.wydad.digital.ticket.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Catégories de billets du stade.
 *
 * <p>Le couple {@code (code, label, defaultPrice)} est exposé au front admin
 * via {@code GET /api/ticket/categories} pour piloter le sélecteur de la
 * grille tarifaire (l'admin n'a plus à saisir la valeur de l'enum ni un
 * prix par défaut à la main).</p>
 *
 * <p>Le {@code defaultPrice} reste un <strong>pré-remplissage</strong> côté
 * formulaire admin : l'admin peut l'écraser section par section (les prix
 * varient selon le match : derby vs match amical, etc.). Le prix persisté
 * reste sur {@code sections.price}.</p>
 */
@Getter
public enum TicketCategory {
    TRIBUNE_OFFICIELLE("Tribune Officielle", new BigDecimal("300.00")),
    TRIBUNE_HONNEUR("Tribune d'Honneur", new BigDecimal("200.00")),
    VIRAGE_NORD("Virage Nord", new BigDecimal("50.00")),
    VIRAGE_SUD("Virage Sud", new BigDecimal("50.00")),
    VIP("VIP", new BigDecimal("500.00")),
    ULTRA("Ultra", new BigDecimal("80.00"));

    /** Libellé lisible (affiché dans le <select> admin et la home). */
    private final String label;

    /** Prix par défaut (pré-rempli dans le formulaire admin). */
    private final BigDecimal defaultPrice;

    TicketCategory(String label, BigDecimal defaultPrice) {
        this.label = label;
        this.defaultPrice = defaultPrice;
    }
}
