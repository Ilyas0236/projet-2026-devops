package com.wydad.digital.auth.repository.subscription;

import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.model.subscription.UserSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
