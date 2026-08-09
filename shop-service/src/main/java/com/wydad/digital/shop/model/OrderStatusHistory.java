package com.wydad.digital.shop.model;

import com.wydad.digital.shop.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private ShopOrder order;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String comment;

    @CreationTimestamp
    private LocalDateTime changedAt;
}