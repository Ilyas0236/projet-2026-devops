package com.wydad.digital.auth.dto.subscription;

import com.wydad.digital.auth.model.subscription.SubscriptionPlan;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.model.subscription.UserSubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vue d'un abonnement souscrit.
 *
 * Champs plan (id, benefits) : null si l'abonnement pointe sur un plan
 * legacy (FK non backfillée) — dans ce cas on retombe sur zoneCode/zoneDisplayName
 * via l'enum historique. Le front affiche l'un OU l'autre, jamais les deux.
 *
 * <p><b>Null-safety (commit quality-final) :</b> {@code zoneCode} peut
 * être {@code null} même si l'entité le déclare {@code nullable=false},
 * car la colonne legacy est progressivement délaissée au profit de
 * {@code plan_id} (cf. SubscriptionService#toLegacyZone qui peut renvoyer
 * null pour un plan 100% admin). Le DTO doit donc tolérer les deux
 * configurations et ne jamais planter en NPE.</p>
 */
public record SubscriptionResponse(
        Long id,
        String email,
        String planCode,
        String planName,
        Long planId,
        String benefits,
        /** Legacy — déduit du plan si absent. */
        String zoneCode,
        String zoneDisplayName,
        String season,
        BigDecimal paidAmount,
        String transactionRef,
        LocalDateTime paidAt,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        UserSubscriptionStatus status,
        String qrCodeBase64,
        String pdfPath
) {
    public static SubscriptionResponse from(UserSubscription s) {
        SubscriptionPlan plan = s.getPlan();
        // planCode/planName : priorité à plan (FK), sinon enum legacy, sinon null.
        String planCode = plan != null
                ? plan.getCode()
                : (s.getZoneCode() != null ? s.getZoneCode().getCode() : null);
        String planName = plan != null
                ? plan.getName()
                : (s.getZoneCode() != null ? s.getZoneCode().getDisplayName() : null);
        // Champs legacy : null-safe sur zoneCode pour ne pas planter.
        String legacyCode = s.getZoneCode() != null ? s.getZoneCode().getCode() : null;
        String legacyName = s.getZoneCode() != null ? s.getZoneCode().getDisplayName() : null;
        return new SubscriptionResponse(
                s.getId(),
                s.getUser().getEmail(),
                planCode,
                planName,
                plan != null ? plan.getId() : null,
                plan != null ? plan.getBenefits() : null,
                legacyCode,
                legacyName,
                s.getSeason(),
                s.getPaidAmount(),
                s.getTransactionRef(),
                s.getPaidAt(),
                s.getValidFrom(),
                s.getValidTo(),
                s.getStatus(),
                s.getQrCodeBase64(),
                s.getPdfPath()
        );
    }
}
