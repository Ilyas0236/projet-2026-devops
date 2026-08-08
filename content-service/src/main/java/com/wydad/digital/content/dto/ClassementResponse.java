package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.SportSection;

public record ClassementResponse(
        Long id,
        Integer position,
        String equipe,
        Integer joues,
        Integer gagnes,
        Integer nuls,
        Integer perdus,
        Integer bp,
        Integer bc,
        Integer points,
        String competition,
        SportSection sport
) {}