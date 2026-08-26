package com.wydad.digital.content.dto;

import com.wydad.digital.content.model.MatchCategory;
import com.wydad.digital.content.model.MatchStatut;
import com.wydad.digital.content.model.SportSection;

import java.time.LocalDate;
import java.time.LocalTime;

public record MatchResponse(
        Long id,
        LocalDate date,
        LocalTime heure,
        String adversaire,
        String adversaireLogoUrl,
        String competition,
        String lieu,
        Integer scoreWydad,
        Integer scoreAdversaire,
        MatchStatut statut,
        SportSection sport,
        MatchCategory categorie
) {}
