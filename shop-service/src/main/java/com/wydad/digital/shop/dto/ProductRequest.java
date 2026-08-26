package com.wydad.digital.shop.dto;

import com.wydad.digital.shop.enums.SportSection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Requête admin pour créer / mettre à jour un produit boutique.
 */
@Data
public class ProductRequest {
    @NotBlank
    private String name;

    private String description;

    @NotNull @Positive
    private BigDecimal basePrice;

    private String mainImageUrl;

    @NotNull
    private SportSection sportSection;

    /** Nom (ou slug) de la catégorie existante ; optionnel. */
    private String categoryName;

    /** Stock initial : crée une variante UNIQUE si aucune taille n'est précisée. */
    private Integer stockQuantity;

    /**
     * Édition par taille (vêtements : S, M, L, XL, XXL…) — remplace la
     * variante UNIQUE implicite quand fournie. Vide ⇒ comportement historique.
     */
    @Valid
    private List<VariantRequest> variants;

    /** SKU optionnel ; généré depuis le nom si absent. */
    private String sku;

    private Boolean active = true;

    @Data
    public static class VariantRequest {
        /** Nom de l'enum ProductSize (XS, S, M, L, XL, XXL, XXXL, UNIQUE). */
        @NotBlank
        private String size;
        private Integer stockQuantity;
        private String color;
        private String colorHex;
    }
}
