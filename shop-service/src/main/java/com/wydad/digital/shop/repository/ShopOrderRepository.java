package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.ShopOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
    Optional<ShopOrder> findByOrderNumber(String orderNumber);
    Optional<ShopOrder> findByOrderNumberAndUserEmail(String orderNumber, String userEmail);
    Page<ShopOrder> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);
    Page<ShopOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * B.12 — Inventaire admin : filtre par date + email + (option) nom de
     * produit. Le filtre produit est appliqué via une sous-requête EXISTS
     * sur OrderItem (LEFT JOIN) pour ne rater aucun item.
     */
    @Query("""
            SELECT DISTINCT o FROM ShopOrder o
              WHERE (:startDate IS NULL OR o.createdAt >= :startDate)
                AND (:endDate   IS NULL OR o.createdAt <= :endDate)
                AND (:userEmail IS NULL OR LOWER(o.userEmail) LIKE LOWER(CONCAT('%', :userEmail, '%')))
                AND (:productName IS NULL OR EXISTS (
                      SELECT 1 FROM OrderItem i
                       WHERE i.order = o
                         AND LOWER(i.productName) LIKE LOWER(CONCAT('%', :productName, '%'))
                ))
            ORDER BY o.createdAt DESC
            """)
    Page<ShopOrder> adminFilter(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userEmail") String userEmail,
            @Param("productName") String productName,
            Pageable pageable);
}