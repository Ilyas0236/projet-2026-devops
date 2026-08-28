package com.wydad.digital.auth.repository.subscription;

import com.wydad.digital.auth.model.subscription.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    /** Catalogue public : plans actifs, triés par ordre d'affichage croissant. */
    List<SubscriptionPlan> findByIsActiveTrueOrderByDisplayOrderAsc();

    /** Inventaire admin : tous les plans, filtre actif optionnel. */
    Page<SubscriptionPlan> findByIsActive(Boolean isActive, Pageable pageable);

    Optional<SubscriptionPlan> findByCode(String code);

    /** Garde la contrainte UNIQUE(code) au niveau applicatif (uniqueness
     *  testée avant l'insert/update). */
    boolean existsByCodeAndIdNot(String code, Long id);

    // existsByPlan_Id appartient à UserSubscriptionRepository (entité
    // UserSubscription) — pas à ce repository, dont l'entité SubscriptionPlan
    // n'a pas de propriété 'plan'. Voir UserSubscriptionRepository#existsByPlan_Id.
}
