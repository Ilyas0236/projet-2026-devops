package com.wydad.digital.shop.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_email", "product_variant_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "user_email")
    private String userEmail;

    @Column(nullable = false, name = "product_variant_id")
    private Long productVariantId;

    private Long productId;
    private String productName;
    private String productImage;
    private String variantInfo;

    @Column(nullable = false)
    private Integer quantity;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customization_id")
    private JerseyCustomization customization;
}