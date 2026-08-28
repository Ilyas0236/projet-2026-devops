package com.wydad.digital.auth.dto.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Données de création/édition d'un plan d'abonnement par l'ADMIN.
 * La validation est ici (et pas dans l'entité) pour produire un 400
 * lisible côté front au lieu d'un 500 Hibernate.
 */
public record SubscriptionPlanUpsertRequest(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[A-Z0-9_-]{1,32}$",
                message = "code doit être en MAJUSCULES/chiffres/_/- uniquement")
        String code,

        @NotBlank
        @Size(max = 128)
        String name,

        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal regularPrice,

        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal adherentPrice,

        String benefits,

        Boolean isActive,

        Integer displayOrder,

        Boolean exceptionalPriority,

        @Size(max = 16)
        String season
) {}
