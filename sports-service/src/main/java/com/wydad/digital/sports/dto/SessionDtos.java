package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs additionnels pour la convocation de séance.
 *
 * <p>{@link SessionDto} reste la représentation « légère » d'une séance
 * (titre, date, lieu, groupe). Le DTO enrichi
 * {@link SessionWithPlayersResponse} embarque en plus la liste des joueurs
 * convoqués, pour la vue ADMIN.</p>
 */
public final class SessionDtos {

    private SessionDtos() {
    }

    /** Joueur convoqué, dénormalisé pour l'affichage admin. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConvokedPlayer {
        private Long joueurUserId;
        private String fullName;
        private Integer jerseyNumber;
    }

    /** Séance + joueurs convoqués — réponse de {@code GET /sessions/admin}. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionWithPlayersResponse {
        private Long id;
        private String title;
        private String description;
        private String location;
        private LocalDateTime sessionDate;
        private SportType sportType;
        private Category category;
        private Long createdByStaffId;
        private LocalDateTime createdAt;
        private List<ConvokedPlayer> convokedPlayers;
    }

    /** Séance simple côté joueur connecté (vue « Mes convocations »). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyConvokedSession {
        private Long id;
        private String title;
        private String description;
        private String location;
        private LocalDateTime sessionDate;
        private SportType sportType;
        private Category category;
        private Long createdByStaffId;
    }
}
