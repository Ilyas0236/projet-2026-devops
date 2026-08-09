package com.wydad.digital.shop.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_images")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url;
    private String altText;
    private Integer displayOrder;
    private Boolean primaryImage = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}