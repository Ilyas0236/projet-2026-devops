package com.wydad.digital.auth.model;

/**
 * Circuit de validation des comptes (Phase 0) :
 * un compte à rôle privilégié est créé EN_ATTENTE et ne peut se connecter
 * qu'après validation par un ADMIN (ou est REFUSE avec un motif).
 */
public enum StatutCompte {
    EN_ATTENTE,
    VALIDE,
    REFUSE
}