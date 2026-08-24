package com.wydad.digital.auth.model;

public enum Role {
    VISITEUR,
    ADHERENT,
    PARENT,
    JOUEUR,
    /** Entraîneur (staff technique) — accès espace entraîneur (Phase 3). */
    ENTRAINEUR,
    STAFF,
    /** Journaliste — accréditation presse (Phase 1/2). */
    JOURNALISTE,
    /** Président du club — espace président (Phase 5 bis). */
    PRESIDENT,
    ADMIN
}