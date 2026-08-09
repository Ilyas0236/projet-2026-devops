package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserEmail(String userEmail);
    Optional<CartItem> findByUserEmailAndProductVariantId(String userEmail, Long productVariantId);
    void deleteByUserEmailAndId(String userEmail, Long id);
    void deleteAllByUserEmail(String userEmail);
}