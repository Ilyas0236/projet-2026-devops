package com.wydad.digital.content.model;

/**
 * Catégorie d'âge d'un match (cahier des charges §26) — alignée sur
 * sports-service Category. Un match appartient à UNE discipline + UNE
 * catégorie ; les deux ne sont jamais mélangées entre groupes.
 */
public enum MatchCategory {
    U15,
    U17,
    U18,
    U20,
    SENIOR
}
