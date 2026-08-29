package com.wydad.digital.auth.config;

import com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service dédié au backfill {@code user_subscriptions.plan_id}.
 *
 * <p>Isolé dans un bean séparé (et non dans {@link SubscriptionPlanBackfillRunner})
 * pour que le proxy Spring active réellement le {@code REQUIRES_NEW} :
 * un appel inter-classes passe par le proxy AOP, alors qu'un
 * {@code this.backfill()} intra-classe court-circuite le proxy et la
 * transaction parente reste en place.</p>
 *
 * <p>Le {@code try/catch} est dans le runner, pas ici : ce service
 * propage l'exception au runner qui la swallow + log.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanBackfillService {

    private final UserSubscriptionRepository userSubscriptionRepository;

    /**
     * Backfill idempotent. Tourne dans sa propre transaction : un échec
     * (ex. table {@code user_subscriptions} absente après truncate) ne
     * peut pas faire rollback le seed des plans d'abonnement.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillPlanIdFromZoneCode() {
        int updated = userSubscriptionRepository.backfillPlanIdFromZoneCode();
        if (updated > 0) {
            log.info("SubscriptionPlanBackfillService : {} ligne(s) mise(s) à jour.", updated);
        }
        return updated;
    }
}
