package com.wydad.digital.auth.model.subscription;

/**
 * Cycle de vie d'un abonnement saisonnier.
 *  - PENDING_PAYMENT : créé lors de l'init, avant la confirmation de payment-service
 *  - ACTIVE          : paiement validé, QR + PDF générés
 *  - CANCELLED       : annulé avant activation (paiement échoué, doublon, etc.)
 *  - REPLACED        : l'utilisateur a racheté un autre abonnement pendant la saison
 *                      (carte remplacée — distinct de EXPIRED qui marque la fin
 *                      naturelle de la saison). Cf. SubscriptionService.replaceActiveSubscription.
 *  - EXPIRED         : la date validTo est dépassée
 */
public enum UserSubscriptionStatus {
    PENDING_PAYMENT,
    ACTIVE,
    CANCELLED,
    REPLACED,
    EXPIRED
}
