package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.SportSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JoueurRequest(
        @NotBlank String nom,
        String photoUrl,
        @NotBlank String poste,
        @NotNull Integer age,
        @NotNull Integer numero,
        @NotNull SportSection sport,
        Integer matchsJoues,
        Integer buts,
        Integer passes
) {}