package com.wydad.digital.sports.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/** DTOs du module sondages (B.2). */
public final class PollDtos {

    private PollDtos() {
    }

    /** Création d'un sondage (ADMIN uniquement). */
    public record CreatePollRequest(String question, List<String> options,
                                    LocalDateTime closesAt) {
    }

    /** Sondage tel que vu par un votant : inclut SON vote et les résultats agrégés. */
    @Builder
    public record PollResponse(
            Long id,
            String question,
            List<String> options,
            boolean active,
            LocalDateTime closesAt,
            LocalDateTime createdAt,
            Long totalVotes,
            /** Nombre de votes par index d'option (résultat calculé serveur). */
            List<Long> resultsPerOption,
            /** Index choisi par l'utilisateur courant, null s'il n'a pas voté. */
            Integer myVoteIndex) {
    }
}
