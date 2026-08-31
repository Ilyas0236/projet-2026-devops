package com.wydad.digital.auth.dto.press;

import com.wydad.digital.auth.model.press.PressAccreditation;
import com.wydad.digital.auth.model.press.PressAccreditationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * B.17 — Vue d'une demande d'accréditation (journaliste ou admin).
 * On n'expose pas l'objet User complet : juste les infos utiles.
 */
public record PressAccreditationResponse(
        Long id,
        Long matchId,
        String matchLabel,
        LocalDate matchDate,
        String organismePresse,
        PressAccreditationStatus statut,
        String motifRefus,
        LocalDateTime createdAt,
        LocalDateTime decidedAt,
        // Champs "vue admin" — null côté journaliste
        String journalistEmail,
        String journalistFirstName,
        String journalistLastName,
        String journalistPhotoUrl,
        String journalistNumeroCartePresse
) {
    public static PressAccreditationResponse from(PressAccreditation a, boolean adminView) {
        if (a == null) return null;
        return new PressAccreditationResponse(
                a.getId(),
                a.getMatchId(),
                a.getMatchLabel(),
                a.getMatchDate(),
                a.getOrganismePresse(),
                a.getStatut(),
                a.getMotifRefus(),
                a.getCreatedAt(),
                a.getDecidedAt(),
                adminView && a.getUser() != null ? a.getUser().getEmail() : null,
                adminView && a.getUser() != null ? a.getUser().getFirstName() : null,
                adminView && a.getUser() != null ? a.getUser().getLastName() : null,
                adminView && a.getUser() != null ? a.getUser().getPhotoUrl() : null,
                adminView && a.getUser() != null ? a.getUser().getNumeroCartePresse() : null
        );
    }
}
