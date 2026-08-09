package com.wydad.digital.shop.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PromoCodeDto {
    private Long id;
    private String code;
    private String description;
    private BigDecimal discountPercent;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private Integer maxUses;
    private Integer currentUses;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Boolean active;
}