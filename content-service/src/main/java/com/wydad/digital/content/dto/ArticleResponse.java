package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.SportSection;

import java.time.LocalDateTime;

public record ArticleResponse(
        Long id,
        String titre,
        String contenu,
        String imageUrl,
        SportSection sport,
        String auteur,
        boolean published,
        LocalDateTime createdAt
) {}