package com.wydad.digital.content.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validation du type REEL d'un fichier par magic bytes (signature binaire),
 * pas seulement sur le Content-Type declare par le client (forgeable).
 * Un exécutable renomme en .jpg ou envoye avec Content-Type image/png est
 * rejeté ici.
 */
@Component
public class FileTypeValidator {

    /** Types autorises a l'upload (images et PDF uniquement). */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"
    );

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] GIF87A = "GIF87a".getBytes();
    private static final byte[] GIF89A = "GIF89a".getBytes();
    private static final byte[] PDF = "%PDF".getBytes();

    /**
     * @return le type MIME reel detecte, ou null si la signature ne correspond
     *         a aucun type autorise.
     */
    public String detectRealContentType(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if (startsWith(data, JPEG)) return "image/jpeg";
        if (startsWith(data, PNG)) return "image/png";
        if (startsWith(data, GIF87A) || startsWith(data, GIF89A)) return "image/gif";
        // WebP : "RIFF" + 4 octets de taille + "WEBP"
        if (startsWith(data, "RIFF".getBytes()) && matchesAt(data, 8, "WEBP".getBytes())) return "image/webp";
        if (startsWith(data, PDF)) return "application/pdf";
        return null;
    }

    /** Le type declare par le client doit correspondre au type reel detecte. */
    public boolean isAllowed(String declaredContentType, byte[] data) {
        String real = detectRealContentType(data);
        return real != null && real.equals(declaredContentType);
    }

    /**
     * Assainit le nom de fichier original : conserve uniquement le nom simple,
     * sans repertoire (anti traversée "../"), avec caracteres surs.
     */
    public String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "fichier";
        }
        String name = UUID.randomUUID().toString() + "_"
                + originalName.substring(originalName.lastIndexOf('/') + 1)
                        .substring(originalName.lastIndexOf('\\') + 1);
        // Whitelist de caracteres : lettres, chiffres, point, tiret, underscore
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return matchesAt(data, 0, prefix);
    }

    private boolean matchesAt(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
