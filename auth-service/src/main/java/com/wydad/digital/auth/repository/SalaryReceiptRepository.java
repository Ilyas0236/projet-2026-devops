package com.wydad.digital.auth.repository;

import com.wydad.digital.auth.model.SalaryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryReceiptRepository extends JpaRepository<SalaryReceipt, Long> {

    /** Reçus du bénéficiaire — ownership strict (§ sécurité). */
    List<SalaryReceipt> findByUserIdOrderByPaymentDateDesc(Long userId);

    /** Tous les reçus (président / admin). */
    List<SalaryReceipt> findAllByOrderByPaymentDateDesc();
}
