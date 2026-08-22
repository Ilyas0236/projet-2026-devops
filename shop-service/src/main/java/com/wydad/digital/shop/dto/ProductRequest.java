package com.wydad.digital.shop.dto;

import com.wydad.digital.shop.enums.SportSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

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

    /** SKU optionnel ; généré depuis le nom si absent. */
    private String sku;

    private Boolean active = true;
}
