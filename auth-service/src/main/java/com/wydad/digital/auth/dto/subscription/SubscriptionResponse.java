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
        String planCode = plan != null ? plan.getCode() : s.getZoneCode().getCode();
        String planName = plan != null ? plan.getName() : s.getZoneCode().getDisplayName();
        return new SubscriptionResponse(
                s.getId(),
                s.getUser().getEmail(),
                planCode,
                planName,
                plan != null ? plan.getId() : null,
                plan != null ? plan.getBenefits() : null,
                s.getZoneCode().getCode(),
                s.getZoneCode().getDisplayName(),
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
