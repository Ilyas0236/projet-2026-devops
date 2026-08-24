package com.wydad.digital.sports.exception;

/**
 * Phase 3 — le service de stockage des médias (Cloudinary) est
 * momentanément indisponible. Même modèle que CloudinaryIndisponibleException
 * de l'auth-service : message actionnable, détail technique journalisé.
 */
public class MediaIndisponibleException extends RuntimeException {
    public MediaIndisponibleException(String detail) {
        super("Le service de partage de médias est momentanément indisponible. Merci de réessayer dans quelques instants.", null, false, false);
        this.detail = detail;
    }

    /** Détail technique journalisé, jamais renvoyé au client. */
    private final String detail;
    public String getDetail() { return detail; }
}
