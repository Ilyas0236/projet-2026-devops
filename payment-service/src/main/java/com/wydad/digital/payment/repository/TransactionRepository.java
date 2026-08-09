package com.wydad.digital.payment.repository;

import com.wydad.digital.payment.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByEmailOrderByCreatedAtDesc(String email);
}