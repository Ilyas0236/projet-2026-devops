package com.wydad.digital.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vue « don » pour l'inventaire admin des achats.
 * Le DTO n'expose pas {@code balanceAfter} (pas pertinent pour un don)
 * mais expose {@code userFullName} (récupéré via batch lookup vers
 * auth-service dans le contrôleur) pour l'affichage direct dans le
 * tableau de l'admin.
 */
public record DonationResponseDto(
        Long id,
        String userEmail,
        String userFullName,
        BigDecimal amount,
        String description,
        String reference,
        String status,
        LocalDateTime createdAt
) {}
