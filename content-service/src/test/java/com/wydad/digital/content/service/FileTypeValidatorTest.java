package com.wydad.digital.content.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTypeValidatorTest {

    private final FileTypeValidator validator = new FileTypeValidator();

    @Test
    void accepteUnVraiPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertTrue(validator.isAllowed("image/png", png));
        assertEquals("image/png", validator.detectRealContentType(png));
    }

    @Test
    void accepteUnVraiJpegEtPdf() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertTrue(validator.isAllowed("image/jpeg", jpeg));

        byte[] pdf = "%PDF-1.7\n...".getBytes();
        assertTrue(validator.isAllowed("application/pdf", pdf));
    }

    @Test
    void rejetteUnExecutableDeguiseEnImage() {
        // MZ = signature PE executable Windows, declare image/png par le client
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 3, 0, 0, 0, 4, 0, 0, 0};
        assertFalse(validator.isAllowed("image/png", exe));
        assertNull(validator.detectRealContentType(exe));
    }

    @Test
    void rejetteUnTypeDeclareDifferentDuTypeReel() {
        byte[] pdf = "%PDF-1.7\n...".getBytes();
        // Un PDF declare comme image : refuse meme si la signature est valide
        assertFalse(validator.isAllowed("image/jpeg", pdf));
    }

    @Test
    void rejetteFichierVideOuCourt() {
        assertFalse(validator.isAllowed("image/png", new byte[0]));
        assertFalse(validator.isAllowed("image/png", null));
    }

    @Test
    void sanitizeNeutraliseLaTraverseeDeChemin() {
        String result = validator.sanitizeFileName("../../etc/passwd.jpg");
        assertFalse(result.contains(".."));
        assertFalse(result.contains("/"));
        assertTrue(result.endsWith("passwd.jpg"));

        String backslash = validator.sanitizeFileName("..\\..\\windows\\system32\\evil.png");
        assertFalse(backslash.contains(".."));
        assertFalse(backslash.contains("\\"));
    }

    @Test
    void sanitizeGenereUnPrefixeUnique() {
        String a = validator.sanitizeFileName("photo.png");
        String b = validator.sanitizeFileName("photo.png");
        // Deux uploads du meme nom produisent des noms stockes distincts
        assertEquals(true, !a.equals(b));
    }
}
