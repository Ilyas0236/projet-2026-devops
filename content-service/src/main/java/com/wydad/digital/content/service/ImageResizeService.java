package com.wydad.digital.content.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Resize d'images à l'upload (sponsors, backgrounds, etc.).
 * <p>
 * Stratégie : conserve le ratio, qualité 0.85, sortie JPEG/PNG selon l'entrée.
 * GIF/WEBP/PDF ne sont PAS redimensionnés : on renvoie l'octet-stream original
 * pour préserver les animations (GIF) et éviter de dégrader les PDF.
 * <p>
 * Le redimensionnement est volontairement synchrone et à l'upload : zéro coût
 * à chaque GET, contrairement à un resize CSS front (qui dégraderait le réseau
 * et la bande passante pour un PNG 4K envoyé 100 fois).
 */
@Service
@Slf4j
public class ImageResizeService {

    /**
     * Redimensionne un blob image en conservant le ratio.
     *
     * @param input        contenu binaire original
     * @param contentType  MIME déclaré (image/jpeg, image/png, image/gif, ...)
     * @param targetWidth  largeur cible en pixels (2× retina recommandée)
     * @param targetHeight hauteur cible en pixels
     * @return octets redimensionnés, ou l'input original si non redimensionnable
     */
    public byte[] resize(byte[] input, String contentType, int targetWidth, int targetHeight) throws IOException {
        if (contentType == null) {
            return input;
        }
        String ct = contentType.toLowerCase();
        if (!ct.contains("jpeg") && !ct.contains("png")) {
            // GIF animé, WEBP, PDF, etc. → on garde l'original
            return input;
        }
        try (var bais = new ByteArrayInputStream(input);
             var baos = new ByteArrayOutputStream()) {
            Thumbnails.of(bais)
                    .size(targetWidth, targetHeight)
                    .outputFormat(ct.contains("png") ? "png" : "jpg")
                    .outputQuality(0.85)
                    .toOutputStream(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Resize échoué ({}x{}, contentType={}) : conservation de l'original",
                    targetWidth, targetHeight, contentType, e);
            return input;
        }
    }
}
