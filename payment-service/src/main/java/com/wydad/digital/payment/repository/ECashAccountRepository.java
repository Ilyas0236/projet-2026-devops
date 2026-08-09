package com.wydad.digital.payment.repository;

import com.wydad.digital.payment.model.ECashAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ECashAccountRepository extends JpaRepository<ECashAccount, Long> {
    Optional<ECashAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}