package com.wydad.digital.auth.dto.subscription;

import com.wydad.digital.auth.model.subscription.SubscriptionPlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vue publique d'un plan d'abonnement saisonnier.
 * Différent de {@link SubscriptionZoneResponse} (legacy enum) :
 * expose id, benefits, isActive, exceptionalPriority — données propres
 * à l'entité JPA pilotée par l'admin.
 */
public record SubscriptionPlanResponse(
        Long id,
        String code,
        String name,
        BigDecimal regularPrice,
        BigDecimal adherentPrice,
        String benefits,
        boolean isActive,
        int displayOrder,
        boolean exceptionalPriority,
        String season,
        LocalDateTime updatedAt
) {
    public static SubscriptionPlanResponse from(SubscriptionPlan p) {
        return new SubscriptionPlanResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getRegularPrice(),
                p.getAdherentPrice(),
                p.getBenefits(),
                Boolean.TRUE.equals(p.getIsActive()),
                p.getDisplayOrder() == null ? 0 : p.getDisplayOrder(),
                Boolean.TRUE.equals(p.getExceptionalPriority()),
                p.getSeason(),
                p.getUpdatedAt()
        );
    }
}
