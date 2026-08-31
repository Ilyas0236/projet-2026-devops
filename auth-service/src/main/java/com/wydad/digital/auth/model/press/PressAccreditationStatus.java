package com.wydad.digital.auth.model.press;

/**
 * Cycle de vie d'une demande d'accréditation presse (B.17) :
 *   - EN_ATTENTE : créée par le journaliste, vue par l'admin
 *   - VALIDE     : admin a accepté, badge généré, journaliste peut télécharger
 *   - REFUSE     : admin a refusé avec un motif, journaliste le voit
 *
 * Pas de transition automatique vers EXPIRED : on garde l'historique.
 * (Si on archive un jour, ce sera via une colonne archivedAt, pas un statut.)
 */
public enum PressAccreditationStatus {
    EN_ATTENTE,
    VALIDE,
    REFUSE
}
