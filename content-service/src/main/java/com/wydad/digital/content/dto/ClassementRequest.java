package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.SportSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClassementRequest(
        @NotNull Integer position,
        @NotBlank String equipe,
        @NotNull Integer joues,
        @NotNull Integer gagnes,
        @NotNull Integer nuls,
        @NotNull Integer perdus,
        @NotNull Integer bp,
        @NotNull Integer bc,
        @NotNull Integer points,
        @NotBlank String competition,
        @NotNull SportSection sport
) {}