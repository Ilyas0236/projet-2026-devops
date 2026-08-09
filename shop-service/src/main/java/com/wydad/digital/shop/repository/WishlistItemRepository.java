package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByUserEmail(String userEmail);
    Optional<WishlistItem> findByUserEmailAndProductId(String userEmail, Long productId);
    void deleteByUserEmailAndId(String userEmail, Long id);
}