package com.wydad.digital.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemDto {
    private Long id;

    @NotNull
    private Long productVariantId;

    private Long productId;
    private String productName;
    private String productImage;
    private String variantInfo;

    @Min(1)
    private Integer quantity;

    private JerseyCustomizationDto customization;

    @Data
    @Builder
    public static class JerseyCustomizationDto {
        @Size(max = 12, message = "Le nom ne doit pas dépasser 12 caractères")
        private String playerName;

        @Min(1) @Max(99)
        private Integer playerNumber;

        private String fontFamily;
        private String fontColor;
        private String patchType;
    }
}