package com.wydad.digital.shop.repository;

import com.wydad.digital.shop.model.PromoCode;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    /**
     * Charge le code promo avec un verrou pessimiste afin que l'incrément
     * de currentUses ne puisse pas dépasser maxUses en concurrence.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM PromoCode p WHERE p.code = :code AND p.active = true")
    Optional<PromoCode> findActiveByCodeForUpdate(@Param("code") String code);

    Optional<PromoCode> findByCode(String code);
    Optional<PromoCode> findByCodeAndActiveTrue(String code);
    List<PromoCode> findByActiveTrue();
}