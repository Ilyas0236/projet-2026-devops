package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.MatchConvocation;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs des convocations de match (§8/§9) : préparation par l'entraîneur,
 * soumission à l'Admin, publication sur le site public.
 */
public final class MatchConvocationDtos {

    private MatchConvocationDtos() {
    }

    /** Demande de convocation d'un joueur pour un match (§8). */
    public record ConvocatePlayerRequest(
            Long matchId,
            Long joueurUserId,
            MatchConvocation.PlayerRole playerRole) {
    }

    /** Convocation groupée : N joueurs en un appel (liste cochable). */
    public record BatchMatchConvocationRequest(
            Long matchId,
            List<BatchPlayerEntry> players) {

        public record BatchPlayerEntry(Long joueurUserId, MatchConvocation.PlayerRole playerRole) {
        }
    }

    @Builder
    public record MatchConvocationResponse(
            Long id,
            Long matchId,
            SportType sportType,
            Category category,
            String adversaire,
            Integer jerseyNumber,
            Long joueurUserId,
            String joueurName,
            MatchConvocation.PlayerRole playerRole,
            MatchConvocation.PublicationStatus status,
            LocalDateTime submittedAt,
            LocalDateTime publishedAt,
            LocalDateTime createdAt) {
    }

    /** Bilan d'une convocation groupée. */
    @Builder
    public record BatchResult(
            int created,
            List<MatchConvocationResponse> convocations,
            List<String> rejected) {
    }

    /**
     * Vue publique (site vitrine, §9) : la liste publiée — joueurs,
     * titulaires/remplaçants, discipline + catégorie du match.
     */
    @Builder
    public record PublicConvocationView(
            Long matchId,
            SportType sportType,
            Category category,
            List<PublishedPlayer> titulaires,
            List<PublishedPlayer> remplacants,
            LocalDateTime publishedAt) {

        @Builder
        public record PublishedPlayer(String fullName, Integer jerseyNumber) {
        }
    }
}
