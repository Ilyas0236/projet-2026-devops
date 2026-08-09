package com.wydad.digital.shop.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private String sku;
    private String slug;
    private Boolean customizable;
    private Boolean active;
    private Double averageRating;
    private Integer reviewCount;
    private String sportSection;
    private String categoryName;
    private List<VariantDto> variants;
    private List<String> images;

    @Data
    @Builder
    public static class VariantDto {
        private Long id;
        private String size;
        private String color;
        private String colorHex;
        private Integer stockQuantity;
    }
}