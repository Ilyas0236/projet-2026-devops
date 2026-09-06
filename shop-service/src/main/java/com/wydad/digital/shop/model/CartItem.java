package com.wydad.digital.shop.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    // Snapshot du prix unitaire au moment de l'ajout au panier (évite N+1
    // et fige le prix vu par l'utilisateur jusqu'à la commande).
    // Nullable pour ne pas casser les CartItem créés AVANT ce fix
    // (Hibernate ddl-auto:update ajoute la colonne sans NOT NULL).
    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customization_id")
    private JerseyCustomization customization;
}