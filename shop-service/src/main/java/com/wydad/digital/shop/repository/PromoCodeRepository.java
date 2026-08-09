package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    Optional<PromoCode> findByCode(String code);
    Optional<PromoCode> findByCodeAndActiveTrue(String code);
    List<PromoCode> findByActiveTrue();
}