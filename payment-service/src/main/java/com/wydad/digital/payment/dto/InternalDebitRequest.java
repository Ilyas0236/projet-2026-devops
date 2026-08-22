package com.wydad.digital.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Requête de débit E-cash service-à-service (billetterie, boutique).
 * Uniquement accessible via l'endpoint /internal/debit protégé par
 * X-Internal-Secret — jamais exposé publiquement.
 */
public record InternalDebitRequest(
        @NotBlank String email,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String reference
) {
}
