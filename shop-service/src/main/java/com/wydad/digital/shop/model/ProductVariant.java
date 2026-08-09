package com.wydad.digital.shop.model;

import com.wydad.digital.shop.enums.ProductSize;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_variants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ProductSize size;

    private String color;
    private String colorHex;
    private Integer stockQuantity;
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}