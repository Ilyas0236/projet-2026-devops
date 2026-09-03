package com.wydad.digital.content.model;

/**
 * B.7 + admin-parametres : niveau de sponsor qui détermine la taille de redimensionnement
 * du logo à l'upload (2× retina, le navigateur downscale pour le rendu final).
 * <p>
 * Valeurs stockées en BDD : {@code String} (pas d'enum JPA, pas de migration).
 * Validation côté Java via {@link #fromString(String)}.
 */
public enum SponsorTier {
    /** Sponsor principal : bloc large pleine largeur footer. */
    MAIN_SPONSOR(600, 240),
    /** Partenaire officiel : 3-4 par ligne. */
    OFFICIAL_PARTNER(400, 160),
    /** Fournisseur : 5-6 par ligne. */
    SUPPLIER(300, 120);

    private final int width;
    private final int height;

    SponsorTier(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() { return width; }
    public int height() { return height; }

    /** Parse tolérant (null/case insensitive). Renvoie null si valeur invalide. */
    public static SponsorTier fromString(String s) {
        if (s == null) return null;
        try {
            return SponsorTier.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
