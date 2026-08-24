package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.MedicalStatus;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Convocation;
import com.wydad.digital.sports.model.PlayerDocument;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
            /** Phase 3 — accusé de lecture (null = pas encore vue par le joueur). */
            LocalDateTime readAt,
            LocalDateTime createdAt) {
    }

    /** Corps de la requête de réponse du joueur à une convocation. */
    public record RespondRequest(Convocation.ResponseStatus status, String justification) {
    }

    @Builder
    public record PlayerDocumentResponse(
            Long id,
            String title,
            String url,
            LocalDateTime dateAjout,
            /** Phase 3 — enrichissement média. */
            PlayerDocument.MediaType mediaType,
            String message,
            Long senderUserId,
            String senderName,
            String publicId) {
    }

    /**
     * Phase 3 — envoi d'un média tactique : UN joueur ({@code joueurUserId})
     * OU toute la catégorie de l'entraîneur ({@code wholeTeam=true}).
     */
    public record ShareMediaRequest(
            Long joueurUserId,
            boolean wholeTeam,
            String title,
            String message,
            PlayerDocument.MediaType mediaType) {
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

    // ───────────────── Phase 3 — convocation groupée & suivi staff ─────────────────

    /**
     * Demande de convocation groupée (« liste cochable ») : une séance +
     * N joueurs en un seul appel. Le serveur rejoue les règles individuelles
     * (anti-doublon, INAPTE, ownership catégorie) pour CHAQUE joueur visé.
     */
    public record BatchConvocationRequest(List<Long> joueurUserIds, Long sessionId) {
    }

    /** Bilan d'un appel groupé : créées + rejets motivés joueur par joueur. */
    @Builder
    public record BatchConvocationResponse(
            int created,
            List<ConvocationResponse> convocations,
            List<BatchRejection> rejected) {
    }

    /** Rejet d'une convocation individuelle dans un appel groupé. */
    @Builder
    public record BatchRejection(
            Long joueurUserId,
            String reason) {
    }

    /**
     * Vue entraîneur d'une convocation d'une séance : identité du joueur
     * (résolue par le service), réponse présence ET accusé de lecture.
     */
    @Builder
    public record StaffConvocationView(
            Long id,
            Long joueurUserId,
            String joueurName,
            Convocation.ResponseStatus responseStatus,
            String responseJustification,
            LocalDateTime respondedAt,
            LocalDateTime readAt,
            LocalDateTime createdAt) {
    }

    /** Synthèse de suivi pour l'entraîneur (compteurs d'une séance). */
    @Builder
    public record SessionResponsesSummary(
            Long sessionId,
            long total,
            long unread,
            long confirmed,
            long absent,
            long late,
            long pending) {
    }
}
