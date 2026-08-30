package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.subscription.UserSubscription;

import java.time.LocalDateTime;

/**
 * Carte de membre — désormais 100% dérivée de l'abonnement saisonnier
 * acheté (SubscriptionPlan + UserSubscription). Si l'utilisateur n'a
 * AUCUN abonnement ACTIF, le contrôleur renvoie 404.
 *
 * <p>L'ancienne enum {@code MembershipLevel} (ROUGE/OR/DIAMANT/JUNIOR)
 * reste en BDD pour la traçabilité des comptes migrés, mais elle n'est
 * PLUS utilisée pour construire cette carte ni l'attestation PDF.</p>
 */
public record MemberCardResponse(
        String email,
        String firstName,
        String lastName,
        String planCode,
        String planName,
        String season,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String referralCode,
        String qrCodeBase64
) {
    public static MemberCardResponse from(UserSubscription sub, String qrCodeBase64) {
        return new MemberCardResponse(
                sub.getUser().getEmail(),
                sub.getUser().getFirstName(),
                sub.getUser().getLastName(),
                sub.getPlan() != null ? sub.getPlan().getCode() : sub.getZoneCode().getCode(),
                sub.getPlan() != null ? sub.getPlan().getName() : sub.getZoneCode().getDisplayName(),
                sub.getSeason(),
                sub.getValidFrom(),
                sub.getValidTo(),
                sub.getUser().getReferralCode(),
                qrCodeBase64
        );
    }
}
