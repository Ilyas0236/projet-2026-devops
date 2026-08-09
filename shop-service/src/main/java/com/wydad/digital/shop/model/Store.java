package com.wydad.digital.shop.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Store {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String city;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String openingHours;
    private Boolean instantJerseyPrinting = false;
    private Boolean active = true;
}