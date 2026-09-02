package com.wydad.digital.auth.dto.subscription;

import java.time.LocalDateTime;

/**
 * B.8 — Vue « titulaire actif » consommée par election-service
 * (dropdown candidats président) et tout service qui doit lister
 * les membres en règle.
 *
 * <p>Calcul : {@code user_subscriptions.status = ACTIVE}
 * ET {@code validTo > now()}. L'éligibilité au vote président
 * est strictement cette condition (cf. {@code InternalMembershipController}).</p>
 *
 * <p>Champs volontairement minimaux (pas de téléphone, pas de
 * photo) : ce DTO est diffusé via un endpoint interne (gateway
 * block) et reste restreint aux microservices de confiance.</p>
 */
public record ActiveMemberDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        String season,
        LocalDateTime validTo,
        Long subscriptionId
) {
    public static ActiveMemberDTO of(com.wydad.digital.auth.model.User user,
                                     com.wydad.digital.auth.model.subscription.UserSubscription sub) {
        return new ActiveMemberDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                sub.getSeason(),
                sub.getValidTo(),
                sub.getId()
        );
    }
}
