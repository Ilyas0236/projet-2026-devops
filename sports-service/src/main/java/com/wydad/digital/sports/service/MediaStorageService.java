package com.wydad.digital.sports.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Phase 3 — stockage des médias tactiques (vidéo/photo/PDF) sur Cloudinary.
 *
 * Même modèle que les justificatifs KYC (auth-service) : upload multipart
 * signé par le backend, folder dédié {@code tactical-media}, ressource
 * « authenticated » — consultation via URL signée générée à la demande.
 * Mode dégradé sans clés (dev local) : référence locale, circuit inchangé.
 */
@Service
public class MediaStorageService {

    private final Cloudinary cloudinary;
    private final boolean configured;

    public MediaStorageService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.configured = cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
        this.cloudinary = configured
                ? new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName,
                        "api_key", apiKey,
                        "api_secret", apiSecret,
                        "secure", true))
                : null;
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Résultat d'un upload : identifiant public + URL sécurisée. */
    public record UploadResult(String publicId, String secureUrl, boolean cloud) {}

    /** Limite applicative des médias tactiques (25 Mo), doublant la limite servlet. */
    public static final long MAX_MEDIA_SIZE_BYTES = 25L * 1024 * 1024;

    /**
     * Upload d'un média tactique (vidéo/photo/PDF, max 25 Mo pour laisser
     * la place aux vidéos courtes d'analyse).
     */
    @SuppressWarnings("unchecked")
    public UploadResult uploadMedia(MultipartFile file, String senderEmail) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide.");
        }
        byte[] data = file.getBytes();
        if (data.length > MAX_MEDIA_SIZE_BYTES) {
            // MaxUploadSizeExceededException -> handler dédié -> 413
            // PAYLOAD_TOO_LARGE (un dépassement n'est pas une faute de saisie).
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(
                    MAX_MEDIA_SIZE_BYTES, new IllegalArgumentException("Fichier trop volumineux (max. 25 Mo)."));
        }

        // Mode dégradé sans clés (dev local) : référence locale, circuit inchangé.
        if (!configured) {
            return new UploadResult("local:staff/" + senderEmail + ":" + file.getOriginalFilename(),
                    null, false);
        }

        String folder = "tactical-media/" + senderEmail.replaceAll("[^a-zA-Z0-9._@-]", "_");
        Uploader uploader = cloudinary.uploader();
        Map<String, Object> result = uploader.upload(data, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto",
                "type", "authenticated"
        ));
        return new UploadResult(
                String.valueOf(result.get("public_id")),
                String.valueOf(result.get("secure_url")),
                true);
    }

    /** Taille maximum acceptée, exposée au frontend pour validation. */
    public static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;

    /**
     * URL signée temporaire (1 h) pour consulter un média — utilisée par le
     * joueur destinataire et par le staff émetteur. Retourne null en mode
     * dégradé ou si l'identifiant n'est pas un identifiant Cloudinary.
     */
    public String signedUrl(String publicId, String storedSecureUrl) {
        if (!configured || publicId == null || !publicId.startsWith("tactical-media/")) {
            return null;
        }
        String resourceType = "image";
        if (storedSecureUrl != null) {
            if (storedSecureUrl.contains("/raw/")) {
                resourceType = "raw";
            } else if (storedSecureUrl.contains("/video/")) {
                resourceType = "video";
            }
        }
        try {
            @SuppressWarnings("unchecked")
            String url = cloudinary.url()
                    .resourceType(resourceType)
                    .type("authenticated")
                    .signed(true)
                    .secure(true)
                    .generate(publicId);
            return url;
        } catch (Exception e) {
            return null;
        }
    }
}
