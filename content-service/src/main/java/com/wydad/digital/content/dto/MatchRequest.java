package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.MatchStatut;
import com.wydad.digital.content.model.SportSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record MatchRequest(
        @NotNull LocalDate date,
        @NotNull LocalTime heure,
        @NotBlank String adversaire,
        @NotBlank String competition,
        @NotBlank String lieu,
        Integer scoreWydad,
        Integer scoreAdversaire,
        @NotNull MatchStatut statut,
        @NotNull SportSection sport
) {}