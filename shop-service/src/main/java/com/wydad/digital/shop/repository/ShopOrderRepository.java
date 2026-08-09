package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.ShopOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
    Optional<ShopOrder> findByOrderNumber(String orderNumber);
    Optional<ShopOrder> findByOrderNumberAndUserEmail(String orderNumber, String userEmail);
    Page<ShopOrder> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);
}