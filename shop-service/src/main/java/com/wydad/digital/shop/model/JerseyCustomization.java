package com.wydad.digital.shop.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "jersey_customizations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JerseyCustomization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 12)
    private String playerName;

    private Integer playerNumber; // 1-99
    private String fontFamily;
    private String fontColor;
    private String patchType;
    private Double extraPrice;
}