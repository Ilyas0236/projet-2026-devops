package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.SportSection;

public record JoueurResponse(
        Long id,
        String nom,
        String photoUrl,
        String poste,
        Integer age,
        Integer numero,
        SportSection sport,
        Integer matchsJoues,
        Integer buts,
        Integer passes
) {}