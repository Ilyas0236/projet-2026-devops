package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    /** Garde-fou : une variante référencée par une commande ne doit jamais être supprimée. */
    boolean existsByVariantId(Long variantId);
}