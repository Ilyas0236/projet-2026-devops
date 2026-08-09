package com.wydad.digital.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrderRequestDto {
    @NotBlank
    private String shippingAddress;

    @NotBlank
    private String shippingCity;

    @NotBlank
    private String shippingPhone;

    private String promoCode;
    private Boolean clickAndCollect;
    private Long pickupStoreId;

    @NotEmpty
    private List<OrderItemRequest> items;

    @Data
    @Builder
    public static class OrderItemRequest {
        private Long cartItemId;
        private CartItemDto.JerseyCustomizationDto customization;
    }
}