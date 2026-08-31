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
     * Upload d'une photo de carte d'abonnement (PNG/JPG, max 5 Mo) dans le
     * folder public {@code subscription-cards/<code-plan>}. Le résultat
     * est une URL directement consultable par les visiteurs (pas d'URL signée).
     *
     * <p>Différent de {@link #uploadKycDocument} : le type de livraison est
     * {@code upload} (public) et non {@code authenticated}, parce que la photo
     * doit être visible sur la home et la page /abonnement sans authentification.
     * Le folder est préfixé par le code du plan (sanitisé) pour pouvoir
     * éventuellement écraser l'image d'un même plan en re-uploadant.</p>
     */
    @SuppressWarnings("unchecked")
    public UploadResult uploadPlanCardImage(MultipartFile file, String planCode) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide.");
        }
        byte[] data = file.getBytes();
        if (data.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Fichier trop volumineux (max. 5 Mo).");
        }

        // Mode dégradé sans clés (dev local) : on garde la même signature
        // (publicId+secureUrl) pour ne pas coupler l'API au fait qu'on ait
        // des clés Cloudinary. secureUrl=null → le front saura qu'il n'y a
        // pas d'image réelle.
        if (!configured) {
            return new UploadResult("local:plan:" + planCode, null, false);
        }

        String safeCode = planCode == null ? "unknown"
                : planCode.replaceAll("[^A-Z0-9_-]", "_");
        String folder = "subscription-cards/" + safeCode;
        Uploader uploader = cloudinary.uploader();
        Map<String, Object> result = uploader.upload(data, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image",
                // Public : la photo est servie directement par Cloudinary.
                "type", "upload",
                // Écrase l'image précédente du même folder pour que l'admin
                // puisse remplacer la photo sans créer d'orphelins.
                "overwrite", true
        ));
        return new UploadResult(
                String.valueOf(result.get("public_id")),
                String.valueOf(result.get("secure_url")),
                true);
    }

    /**
     * B.17 — Upload d'une photo de profil journaliste (JPEG/PNG/WebP, max 5 Mo)
     * dans le folder public {@code profile-photos/journalist-{userId}}.
     *
     * <p>Type {@code upload} (public, pas d'URL signée) car la photo est
     * destinée à être affichée sur le badge d'accréditation et dans
     * l'espace admin. Le folder est préfixé par l'id utilisateur (pas
     * l'email) pour qu'un re-upload écrase proprement la photo précédente
     * sans laisser d'orphelins.</p>
     *
     * <p>Référence : {@code MediaStorageService.uploadProfilePhoto}
     * (sports-service l. 99-137) qui applique les mêmes contraintes
     * (5 Mo, JPEG/PNG/WebP, type image public).</p>
     */
    @SuppressWarnings("unchecked")
    public UploadResult uploadProfilePhoto(MultipartFile file, Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier photo vide.");
        }
        byte[] data = file.getBytes();
        if (data.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Photo trop volumineuse (max. 5 Mo).");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !(contentType.equalsIgnoreCase("image/jpeg")
                  || contentType.equalsIgnoreCase("image/jpg")
                  || contentType.equalsIgnoreCase("image/png")
                  || contentType.equalsIgnoreCase("image/webp"))) {
            throw new IllegalArgumentException("Format de photo non supporté (JPEG, PNG ou WebP uniquement).");
        }

        // Mode dégradé sans clés : même signature que KYC, on garde la
        // trace locale pour que le back reste fonctionnel en dev.
        if (!configured) {
            return new UploadResult("local:profile:" + userId, null, false);
        }

        String folder = "profile-photos/journalist-" + userId;
        Uploader uploader = cloudinary.uploader();
        Map<String, Object> result = uploader.upload(data, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image",
                "type", "upload",
                "overwrite", true,
                // Transformation à la volée : on cadre la photo en carré
                // 400x400 pour l'affichage badge / espace admin, sans
                // dégrader la qualité perçue.
                "transformation", "c_fill,g_face,w_400,h_400,q_auto,f_auto"
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
                    // Les justificatifs sont uploadés en type "authenticated" :
                    // l'URL signée doit porter ce delivery type, sinon 404.
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
