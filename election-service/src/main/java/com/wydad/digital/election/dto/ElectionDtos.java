package com.wydad.digital.election.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs des élections présidentielles. Les résultats (nombre de voix par
 * candidat, gagnant, pourcentages) sont TOUJOURS calculés côté serveur.
 */
public final class ElectionDtos {

    private ElectionDtos() {}

    @Value
    @Builder
    public static class CreateElectionRequest {
        @NotBlank(message = "Le titre de l'élection est obligatoire")
        String title;
        @NotNull(message = "La date de début est obligatoire")
        LocalDateTime startsAt;
        @NotNull(message = "La date de fin est obligatoire")
        LocalDateTime endsAt;
        @Valid
        List<AddCandidateRequest> candidates;
    }

    /**
     * Candidat (création et ajout) : photo déjà uploadée côté front (URL Cloudinary publique).
     *
     * <p>B.8 — le champ {@code userId} est désormais obligatoire pour qu'un
     * candidat puisse figurer sur un scrutin (il doit être titulaire d'une
     * carte d'abonnement active). Il reste techniquement nullable au niveau
     * API pour permettre la saisie en 2 temps (admin crée le candidat
     * « externe » puis lie le titulaire avant clôture) — la validation
     * d'éligibilité est faite côté service (cf. {@code ElectionService.saveCandidate}).</p>
     */
    @Value
    public static class AddCandidateRequest {
        @NotBlank(message = "Le nom du candidat est obligatoire")
        String fullName;
        @Size(max = 1000, message = "La présentation ne peut pas dépasser 1000 caractères")
        String presentation;
        String photoUrl;
        Integer displayOrder;
        Long userId;
    }

    @Value
    @Builder
    public static class CandidateView {
        Long id;
        String fullName;
        String presentation;
        String photoUrl;
        int displayOrder;
        Long userId;
    }

    /**
     * Vue d'une élection.
     *
     * Sécurité : `results` n'est peuplé QUE si l'élection est clôturée
     * (published) — avant clôture les comptages restent secrets ; après,
     * ils sont publics y compris aux visiteurs non connectés.
     */
    @Value
    @Builder
    public static class ElectionView {
        Long id;
        String title;
        LocalDateTime startsAt;
        LocalDateTime endsAt;
        String status;          // OPEN | CLOSED
        boolean published;
        /** Id du candidat gagnant — peuplé seulement si published. */
        Long winnerCandidateId;
        List<CandidateView> candidates;
        long totalVotes;
        /** Nombre de voix par candidat — vide avant publication. */
        List<Long> results;
        /** Pourcentage arrondi par candidat (ordre des candidats) — vide avant publication. */
        List<Integer> percentages;
        /** Index du vote de l'utilisateur courant (null s'il n'a pas voté). */
        Integer myVoteIndex;
        boolean canVote;
    }
}
