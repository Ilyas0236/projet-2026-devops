package com.wydad.digital.shop.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoreDto {
    private Long id;
    private String name;
    private String address;
    private String city;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String openingHours;
    private Boolean instantJerseyPrinting;
    private Boolean active;
}