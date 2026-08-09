package com.wydad.digital.shop.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponseDto {
    private String orderNumber;
    private String status;
    private String paymentStatus;
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String trackingNumber;
    private LocalDateTime createdAt;
    private List<OrderItemDto> items;

    @Data
    @Builder
    public static class OrderItemDto {
        private String productName;
        private String productImage;
        private String variantInfo;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }
}