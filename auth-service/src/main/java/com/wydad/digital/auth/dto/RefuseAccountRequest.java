package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Phase 0 — corps typé du refus d'un compte.
 *
 * Avant : le contrôleur attendait une chaîne brute (@RequestBody String) —
 * Jackson désérialisait {} en la chaîne littérale "{}", qui passe @NotBlank
 * (non blanche !), et le refus partait avec un motif poubelle. Un DTO typé +
 * @Valid rend la validation réelle : {} → champ manquant → 400.
 */
@Data
public class RefuseAccountRequest {
    @NotBlank(message = "Le motif de refus est obligatoire")
    private String motif;
}
