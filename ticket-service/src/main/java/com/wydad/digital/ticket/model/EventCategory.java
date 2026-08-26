package com.wydad.digital.ticket.model;

/**
 * Catégorie d'âge d'un événement (cahier des charges §26) — alignée sur
 * sports-service Category. Un événement appartient à UNE discipline
 * (eventType) + UNE catégorie ; les deux ne sont jamais mélangées.
 */
public enum EventCategory {
    U15,
    U17,
    U18,
    U20,
    SENIOR
}
