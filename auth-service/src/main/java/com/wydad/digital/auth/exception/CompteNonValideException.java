package com.wydad.digital.auth.exception;

import com.wydad.digital.auth.model.StatutCompte;

/**
 * Phase 0 : levée au login/refresh quand les identifiants sont corrects
 * mais que le compte n'est pas encore VALIDE (EN_ATTENTE ou REFUSE).
 * Renvoie 403 avec un message explicite côté client.
 */
public class CompteNonValideException extends RuntimeException {

    private final StatutCompte statut;
    private final String motifRefus;

    public CompteNonValideException(StatutCompte statut, String motifRefus) {
        super(switch (statut) {
            case EN_ATTENTE -> "Votre compte est en attente de validation par le club.";
            case REFUSE -> motifRefus != null && !motifRefus.isBlank()
                    ? "Votre demande de compte a été refusée : " + motifRefus
                    : "Votre demande de compte a été refusée.";
            default -> "Compte non valide.";
        });
        this.statut = statut;
        this.motifRefus = motifRefus;
    }

    public StatutCompte getStatut() {
        return statut;
    }

    public String getMotifRefus() {
        return motifRefus;
    }
}
