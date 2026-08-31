package com.wydad.digital.auth.dto.press;

import jakarta.validation.constraints.NotNull;

/**
 * B.17 — Demande de création d'une accréditation presse par un journaliste.
 * Le serveur résout le libellé du match via content-service (interne) et
 * fige organismePresse / matchLabel sur l'entité PressAccreditation.
 */
public record PressAccreditationRequest(
        @NotNull Long matchId
) {}
