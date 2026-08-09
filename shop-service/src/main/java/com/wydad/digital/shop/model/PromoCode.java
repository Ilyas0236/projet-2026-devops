package com.wydad.digital.shop.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PromoCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    private String description;

    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    private Integer maxUses;
    private Integer currentUses = 0;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Boolean active = true;
}