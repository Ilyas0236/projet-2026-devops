package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTOs des statistiques de match (B.4). */
public final class MatchStatDtos {

    private MatchStatDtos() {
    }

    public record MatchStatRequest(
            String opponent,
            LocalDate matchDate,
            Integer goals,
            Integer assists,
            Integer minutesPlayed,
            String competition) {
    }

    @Builder
    public record MatchStatResponse(
            Long id,
            Long joueurUserId,
            SportType sportType,
            Category category,
            String opponent,
            LocalDate matchDate,
            int goals,
            int assists,
            Integer minutesPlayed,
            String competition,
            LocalDateTime createdAt) {
    }
}
