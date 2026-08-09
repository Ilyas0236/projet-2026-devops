package com.wydad.digital.shop.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wishlist_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WishlistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private Long productId;

    private LocalDateTime addedAt;
}