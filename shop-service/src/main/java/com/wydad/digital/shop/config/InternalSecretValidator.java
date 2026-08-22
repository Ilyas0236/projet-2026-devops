package com.wydad.digital.shop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Valide les appels service-à-service via un secret partagé transmis
 * dans l'en-tête X-Internal-Secret. La comparaison est à temps constant
 * pour éviter les attaques par analyse temporelle.
 */
@Component
public class InternalSecretValidator {

    private final byte[] expectedDigest;

    public InternalSecretValidator(@Value("${wydad.internal-secret:}") String secret) {
        this.expectedDigest = sha256(secret == null ? "" : secret);
    }

    /** Vrai si le secret fourni correspond au secret configuré (non vide). */
    public boolean isInternalCallAuthorized(String provided) {
        if (expectedDigest.length == 0 || provided == null || provided.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, sha256(provided));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
