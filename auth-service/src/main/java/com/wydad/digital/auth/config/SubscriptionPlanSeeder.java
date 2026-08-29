package com.wydad.digital.auth.config;

import com.wydad.digital.auth.model.subscription.SubscriptionPlan;
import com.wydad.digital.auth.model.subscription.SubscriptionZoneCode;
import com.wydad.digital.auth.repository.subscription.SubscriptionPlanRepository;
import com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeder idempotent des plans d'abonnement saisonnier.
 *
 * Synchronise la table {@code subscription_plans} avec l'enum
 * {@link SubscriptionZoneCode} (source de vérité historique) :
 *  - crée un plan si manquant,
 *  - laisse intacts les plans édités par l'admin (les prix/bénéfices
 *    saisis manuellement ne sont jamais écrasés au redémarrage),
 *  - backfill {@code user_subscriptions.plan_id} depuis {@code zone_code}
 *    pour les lignes pré-migration (FK nullable, ON DELETE SET NULL).
 *
 * PEL-4 (sold out) est inséré en {@code isActive=false} pour mémoire.
 * Le tarif adhérent est strictement inférieur au tarif régulier uniquement
 * pour les zones ADHERENT (VIP-A, VVIP-A, VVIP-PA) — pour les autres, les
 * deux prix sont identiques (cf. enum).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanSeeder {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (SubscriptionZoneCode z : SubscriptionZoneCode.values()) {
            String code = z.getCode();
            if (planRepository.findByCode(code).isPresent()) {
                skipped.add(code);
                continue;
            }

            // Convertir les prix int (DH) de l'enum en BigDecimal(10,2).
            BigDecimal regularPrice = BigDecimal.valueOf(z.getPriceRegular());
            BigDecimal adherentPrice = BigDecimal.valueOf(z.getPriceAdherent());

            SubscriptionPlan plan = SubscriptionPlan.builder()
                    .code(code)
                    .name(z.getDisplayName())
                    .regularPrice(regularPrice)
                    .adherentPrice(adherentPrice)
                    // Avantage par défaut vide : l'admin l'édite via le back-office.
                    .benefits(null)
                    // PEL-4 = sold out → désactivé. Les 9 autres → commercialisés.
                    .isActive(!z.isSoldOut())
                    // Ordre d'affichage = position dans l'enum (donc PEL-4 en premier).
                    .displayOrder(z.ordinal())
                    // Seules les 3 zones "Adhérent" donnent la priorité sur les
                    // matchs exceptionnels — on aligne sur la logique existante.
                    .exceptionalPriority(z.name().endsWith("ADHERENT"))
                    .season(null)
                    .build();

            planRepository.save(plan);
            created.add(code);
        }

        if (created.isEmpty()) {
            log.info("SubscriptionPlanSeeder : {} plans déjà présents, rien à créer.",
                    skipped.size());
        } else {
            log.info("SubscriptionPlanSeeder : {} plans créés ({}), {} déjà présents.",
                    created.size(), created, skipped.size());
        }

        // Backfill FK plan_id sur les user_subscriptions pré-migration.
        // Idempotent grâce au WHERE plan_id IS NULL.
        try {
            int updated = userSubscriptionRepository.backfillPlanIdFromZoneCode();
            if (updated > 0) {
                log.info("SubscriptionPlanSeeder : backfill plan_id → {} ligne(s) mise(s) à jour.",
                        updated);
            } else {
                log.debug("SubscriptionPlanSeeder : aucun backfill nécessaire (plan_id déjà renseigné).");
            }
        } catch (Exception ex) {
            // Ne pas planter le démarrage du service si le backfill échoue :
            // on log et on continue (les nouveaux achats fonctionneront, seul
            // l'historique pré-migration restera avec plan_id=NULL).
            log.warn("SubscriptionPlanSeeder : backfill plan_id a échoué ({}) — "
                    + "l'historique pré-migration aura plan_id NULL.", ex.getMessage());
        }
    }
}
