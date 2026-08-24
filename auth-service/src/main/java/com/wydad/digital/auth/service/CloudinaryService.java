package com.wydad.digital.auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Phase 1 — stockage des pièces justificatives (KYC) sur Cloudinary.
 *
 * Le frontend envoie le fichier en multipart à l'auth-service, qui signe et
 * pousse vers Cloudinary (folder privé). Seuls {@code publicId} + URL sécurisée
 * sont stockés en base — jamais de binaire, jamais l'URL brute exposée en public.
 *
 * Si les clés ne sont pas configurées (développement local), le service
 * bascule sur un mode dégradé : le fichier n'est pas envoyé, on stocke une
 * référence locale — le circuit de validation admin reste fonctionnel.
 */
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean configured;

    public CloudinaryService(
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

    /** Résultat d'un upload Cloudinary : identifiant public + URL sécurisée. */
    public record UploadResult(String publicId, String secureUrl, boolean cloud) {}

    /**
     * Upload d'un justificatif (PDF/JPG/PNG) dans le folder privé kyc-documents,
     * préfixé par l'email (sanitisé) du porteur du dossier.
     */
    @SuppressWarnings("unchecked")
    public UploadResult uploadKycDocument(MultipartFile file, String email) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide.");
        }
        byte[] data = file.getBytes();
        if (data.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Fichier trop volumineux (max. 10 Mo).");
        }

        // Mode dégradé sans clés (dev local) : référence locale, circuit inchangé.
        if (!configured) {
            return new UploadResult("local:" + email + ":" + file.getOriginalFilename(),
                    null, false);
        }

        String folder = "kyc-documents/" + email.replaceAll("[^a-zA-Z0-9._@-]", "_");
        Uploader uploader = cloudinary.uploader();
        Map<String, Object> result = uploader.upload(data, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto",
                // Pas d'accès public : la ressource n'est consultable que via
                // son URL signée générée à la demande par le backend.
                "type", "authenticated"
        ));
        return new UploadResult(
                String.valueOf(result.get("public_id")),
                String.valueOf(result.get("secure_url")),
                true);
    }

    /**
     * URL signée temporaire (1 h) pour consulter un justificatif — utilisée par
     * l'admin lors de la validation du compte. Retourne null en mode dégradé.
     */
    public String signedUrl(String publicId) {
        return signedUrl(publicId, null);
    }

    /**
     * Variante qui déduit le resource_type de l'URL stockée : les images partent
     * en {@code image}, les PDF en {@code raw} (upload resource_type auto) —
     * une URL signée avec le mauvais type ne s'ouvre pas.
     */
    public String signedUrl(String publicId, String storedSecureUrl) {
        if (!configured || publicId == null || !publicId.startsWith("kyc-documents/")) {
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
                    .signed(true)
                    .secure(true)
                    .generate(publicId);
            return url;
        } catch (Exception e) {
            return null;
        }
    }
}
