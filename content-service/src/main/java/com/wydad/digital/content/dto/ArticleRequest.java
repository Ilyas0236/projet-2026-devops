package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.SportSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ArticleRequest(
        @NotBlank String titre,
        @NotBlank String contenu,
        String imageUrl,
        @NotNull SportSection sport,
        @NotBlank String auteur
) {}