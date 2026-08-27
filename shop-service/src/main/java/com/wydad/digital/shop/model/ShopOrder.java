package com.wydad.digital.shop.model;

import com.wydad.digital.shop.enums.OrderStatus;
import com.wydad.digital.shop.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShopOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount;

    /**
     * B.12 — Réduction automatique 15% appliquée aux ADHÉRENTS (titulaires
     * d'un abonnement saisonnier actif). Calculée sur le sous-total,
     * cumulable avec un code promo (l'addition des deux est soustraite au
     * total). 0 si l'utilisateur n'est pas adhérent.
     */
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal adherentDiscount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    private String promoCodeUsed;
    private String shippingAddress;
    private String shippingCity;
    private String shippingPhone;
    private String trackingNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean clickAndCollect = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store pickupStore;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}