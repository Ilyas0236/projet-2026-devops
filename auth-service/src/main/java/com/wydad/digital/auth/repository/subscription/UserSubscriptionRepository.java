package com.wydad.digital.auth.repository.subscription;

import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.model.subscription.UserSubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * Abonnement ACTIF courant d'un utilisateur (au plus 1).
     * La contrainte d'unicité est appliquée en service (pas en base) car
     * MySQL n'a pas d'index partiels — on s'appuie sur cette requête.
     */
    @Query("SELECT s FROM UserSubscription s " +
            "WHERE s.user = :user AND s.status = :status " +
            "ORDER BY s.validTo DESC")
    List<UserSubscription> findByUserAndStatus(@Param("user") User user,
                                              @Param("status") UserSubscriptionStatus status);

    default Optional<UserSubscription> findActiveByUser(User user) {
        List<UserSubscription> actives = findByUserAndStatus(user, UserSubscriptionStatus.ACTIVE);
        return actives.isEmpty() ? Optional.empty() : Optional.of(actives.get(0));
    }

    /** Historique complet d'un utilisateur, plus récent d'abord. */
    List<UserSubscription> findByUserOrderByPaidAtDesc(User user);

    /**
     * B.12 — Inventaire admin des abonnements. Filtre par date + email.
     */
    @Query("""
            SELECT s FROM UserSubscription s
              WHERE (:startDate IS NULL OR s.paidAt >= :startDate)
                AND (:endDate   IS NULL OR s.paidAt <= :endDate)
                AND (:userEmail IS NULL OR LOWER(s.user.email) LIKE LOWER(CONCAT('%', :userEmail, '%')))
            ORDER BY s.paidAt DESC
            """)
    Page<UserSubscription> adminFilter(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userEmail") String userEmail,
            Pageable pageable);

    /**
     * TRUE si au moins un abonnement référence ce plan.
     * Utilisé par {@code SubscriptionPlanService.delete()} pour refuser
     * la suppression d'un plan encore utilisé (renvoyer 409 plutôt que 500).
     */
    boolean existsByPlan_Id(Long planId);
}
