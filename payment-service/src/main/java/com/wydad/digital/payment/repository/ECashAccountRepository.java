package com.wydad.digital.payment.repository;

import com.wydad.digital.payment.model.ECashAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ECashAccountRepository extends JpaRepository<ECashAccount, Long> {
    Optional<ECashAccount> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Lecture avec verrou pessimiste : sérialise les débits concurrents sur un même compte. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM ECashAccount a WHERE a.email = :email")
    Optional<ECashAccount> findByEmailForUpdate(@org.springframework.lang.NonNull String email);
}