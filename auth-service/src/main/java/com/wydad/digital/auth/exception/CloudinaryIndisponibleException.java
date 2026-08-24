package com.wydad.digital.auth.exception;

/**
 * Phase 1 ter — le service d'upload (Cloudinary) est momentanément
 * indisponible : erreur réseau, identifiants refusés, incident côté
 * fournisseur. Distinct d'une faute de l'utilisateur : le message est
 * actionnable (« réessayez »), pas un 400 générique.
 */
public class CloudinaryIndisponibleException extends RuntimeException {
    public CloudinaryIndisponibleException(String detail) {
        super("Le service de dépôt de documents est momentanément indisponible. Merci de réessayer dans quelques instants.", null, false, false);
        this.detail = detail;
    }

    /** Détail technique journalisé, jamais renvoyé au client. */
    private final String detail;
    public String getDetail() { return detail; }
}
