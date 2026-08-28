package com.wydad.digital.communication.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * V2.3 — stockage Cloudinary des pièces jointes de messagerie (image,
 * PDF, document brut). Limite 10 Mo (cohérent avec la limite servlet).
 *
 * <p>Type « authenticated » : les fichiers ne sont pas accessibles par
 * URL publique — il faut une URL signée générée à la volée à chaque
 * consultation. Évite le hot-link et le partage hors conversation.</p>
 *
 * <p>Mode dégradé sans clés Cloudinary (dev local) : un identifiant
 * « local: » est rendu pour ne pas casser le circuit ; l'UI affichera
 * simplement « pièce jointe (mode local) ».</p>
 */
@Service
public class MessageMediaService {

    private final Cloudinary cloudinary;
    private final boolean configured;

    public MessageMediaService(
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

    /** Résultat d'un upload. */
    public record UploadResult(
            String publicId,
            String secureUrl,
            String resourceType,
            String fileName,
            long sizeBytes,
            boolean cloud) {}

    /** Limite applicative — doublon de la limite servlet pour 413 propre. */
    public static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    /**
     * Upload d'une pièce jointe de message (image / PDF / doc, max 10 Mo).
     * Le {@code senderEmail} sert de préfixe de folder pour isoler les
     * fichiers par expéditeur (pas de sécurité forte — Cloudinary ACL
     * « authenticated » fait le reste).
     */
    @SuppressWarnings("unchecked")
    public UploadResult upload(MultipartFile file, String senderEmail) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide.");
        }
        byte[] data = file.getBytes();
        if (data.length > MAX_ATTACHMENT_BYTES) {
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(
                    MAX_ATTACHMENT_BYTES,
                    new IllegalArgumentException("Pièce jointe trop volumineuse (max. 10 Mo)."));
        }

        String original = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "fichier";

        // Mode dégradé sans clés (dev local).
        if (!configured) {
            return new UploadResult(
                    "local:messages/" + senderEmail + ":" + original,
                    null, "raw", original, data.length, false);
        }

        String folder = "message-attachments/" + senderEmail.replaceAll("[^a-zA-Z0-9._@-]", "_");
        Uploader uploader = cloudinary.uploader();
        Map<String, Object> result = uploader.upload(data, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto",
                "type", "authenticated"
        ));
        String publicId = String.valueOf(result.get("public_id"));
        String secureUrl = String.valueOf(result.get("secure_url"));
        String resourceType = String.valueOf(result.getOrDefault("resource_type", "raw"));
        return new UploadResult(publicId, secureUrl, resourceType, original, data.length, true);
    }

    /**
     * URL signée temporaire (1 h par défaut Cloudinary) pour consulter
     * une pièce jointe. {@code resourceType} doit être déduit de l'URL
     * stockée (image | raw | video) car Cloudinary signe par resource.
     */
    public String signedUrl(String publicId, String resourceType) {
        if (!configured || publicId == null || !publicId.startsWith("message-attachments/")) {
            return null;
        }
        String rt = (resourceType == null || resourceType.isBlank()) ? "raw" : resourceType;
        try {
            return cloudinary.url()
                    .resourceType(rt)
                    .type("authenticated")
                    .signed(true)
                    .secure(true)
                    .generate(publicId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Déduction du resource_type à partir d'une URL Cloudinary historique.
     * Helper pratique pour signer à nouveau lors d'une consultation
     * ultérieure (URL stockée a expiré).
     */
    public String detectResourceType(String secureUrl) {
        if (secureUrl == null) return "raw";
        if (secureUrl.contains("/image/")) return "image";
        if (secureUrl.contains("/video/")) return "video";
        return "raw";
    }
}
