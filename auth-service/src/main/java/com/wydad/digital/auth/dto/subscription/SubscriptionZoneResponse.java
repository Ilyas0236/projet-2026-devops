package com.wydad.digital.auth.dto.subscription;

import com.wydad.digital.auth.model.subscription.SubscriptionZoneCode;

/**
 * Vue publique d'une zone d'abonnement.
 * On renvoie DEUX prix : regular (non-adhérent) et adherent (abonné).
 * Le front choisit lequel afficher selon le statut de l'utilisateur.
 */
public record SubscriptionZoneResponse(
        String code,
        String displayName,
        int priceRegular,
        int priceAdherent,
        boolean soldOut
) {
    public static SubscriptionZoneResponse from(SubscriptionZoneCode z) {
        return new SubscriptionZoneResponse(
                z.getCode(),
                z.getDisplayName(),
                z.getPriceRegular(),
                z.getPriceAdherent(),
                z.isSoldOut()
        );
    }
}
