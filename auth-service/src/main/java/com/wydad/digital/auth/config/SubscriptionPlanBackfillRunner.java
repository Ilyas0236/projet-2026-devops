package com.wydad.digital.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Déclencheur du backfill {@code user_subscriptions.plan_id} au démarrage.
 *
 * <p>La logique transactionnelle est dans {@link SubscriptionPlanBackfillService}
 * (REQUIRES_NEW). Ce runner se contente d'appeler le service dans un
 * {@code try/catch} global pour garantir que le service d'authentification
 * démarre quoi qu'il arrive (incident 2026-08-28 : UnexpectedRollbackException
 * car le backfill et le seed partageaient la même transaction).</p>
 *
 * <p>{@code @Order(10)} : exécuté après {@link SubscriptionPlanSeeder}
 * (ordre par défaut 0), pour que les plans soient déjà insérés au moment
 * du UPDATE.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanBackfillRunner {

    private final SubscriptionPlanBackfillService backfillService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void runBackfill() {
        try {
            int updated = backfillService.backfillPlanIdFromZoneCode();
            if (updated > 0) {
                log.info("SubscriptionPlanBackfillRunner : backfill plan_id → {} ligne(s) mise(s) à jour.",
                        updated);
            } else {
                log.debug("SubscriptionPlanBackfillRunner : aucun backfill nécessaire (plan_id déjà renseigné).");
            }
        } catch (Exception ex) {
            // Ne JAMAIS faire remonter cette exception : un échec du backfill
            // ne doit pas empêcher le service d'authentification de démarrer.
            log.warn("SubscriptionPlanBackfillRunner : backfill plan_id a échoué ({}) — "
                    + "les abonnements historiques garderont plan_id=NULL (non bloquant).",
                    ex.getMessage());
        }
    }
}
