package com.wydad.digital.payment.repository;

import com.wydad.digital.payment.model.Transaction;
import com.wydad.digital.payment.model.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByEmailOrderByCreatedAtDesc(String email);

    /**
     * Filtre admin pour l'inventaire des dons : type=DON + dates (optionnelles)
     * + email (optionnel). Tri par date DESC. Utilisé par
     * GET /api/payment/admin/donations/filter.
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.type = :type " +
           "AND t.createdAt >= COALESCE(:startDate, t.createdAt) " +
           "AND t.createdAt <= COALESCE(:endDate,   t.createdAt) " +
           "AND COALESCE(t.email, '') = COALESCE(:email, t.email) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> adminFilterByTypeAndDates(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("email") String email,
            Pageable pageable);
}