package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.MedicalStatus;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Convocation;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTOs de l'espace joueur (B.3) : convocations, réponse de présence,
 * documents partagés, édition de profil à champs restreints.
 */
public final class PlayerSpaceDtos {

    private PlayerSpaceDtos() {
    }

    @Builder
    public record ConvocationResponse(
            Long id,
            Long sessionId,
            String sessionTitle,
            String sessionLocation,
            LocalDateTime sessionDate,
            SportType sportType,
            Category category,
            Convocation.ResponseStatus responseStatus,
            String responseJustification,
            LocalDateTime respondedAt,
            LocalDateTime createdAt) {
    }

    /** Corps de la requête de réponse du joueur à une convocation. */
    public record RespondRequest(Convocation.ResponseStatus status, String justification) {
    }

    @Builder
    public record PlayerDocumentResponse(
            Long id, String title, String url, LocalDateTime dateAjout) {
    }

    /**
     * Champs que le joueur ne peut PAS modifier : statut médical (B.6),
     * numéro, poste, catégorie et sport restent réservés au staff/admin.
     */
    public record UpdateMyProfileRequest(
            Double height,
            Double weight,
            LocalDate birthDate,
            String nationality,
            String photoUrl) {
    }

    /** Réponse de pose de statut médical (B.6). */
    @Builder
    public record MedicalResponse(
            Long joueurUserId,
            MedicalStatus status,
            String note,
            LocalDateTime updatedAt) {
    }
}
