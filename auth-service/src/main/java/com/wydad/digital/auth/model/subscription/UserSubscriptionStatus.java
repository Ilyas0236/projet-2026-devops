package com.wydad.digital.auth.model.subscription;

/**
 * Cycle de vie d'un abonnement saisonnier.
 *  - PENDING_PAYMENT : créé lors de l'init, avant la confirmation de payment-service
 *  - ACTIVE          : paiement validé, QR + PDF générés
 *  - CANCELLED       : annulé avant activation (paiement échoué, doublon, etc.)
 *  - EXPIRED         : la date validTo est dépassée
 */
public enum UserSubscriptionStatus {
    PENDING_PAYMENT,
    ACTIVE,
    CANCELLED,
    EXPIRED
}
