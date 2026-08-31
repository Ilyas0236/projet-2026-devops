package com.wydad.digital.auth.dto.press;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * B.17 — Motif de refus écrit par l'admin (obligatoire).
 * Affiché tel quel dans l'espace journaliste.
 */
public record RefusePressAccreditationRequest(
        @NotBlank @Size(max = 512) String motif
) {}
