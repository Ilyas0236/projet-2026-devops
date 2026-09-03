package com.wydad.digital.gamification.repository;

import com.wydad.digital.gamification.model.PointTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    /** Historique d'un utilisateur, du plus récent au plus ancien. */
    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Pour l'admin : attributions récentes tous utilisateurs confondus. */
    List<PointTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
