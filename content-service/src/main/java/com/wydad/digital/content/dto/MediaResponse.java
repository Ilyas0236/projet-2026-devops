package com.wydad.digital.content.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadonnees d'un fichier mediatheque : jamais le blob binaire,
 * servi separement via GET /api/content/media/{fileName}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaResponse {
    private Long id;
    private String fileName;
    private String originalName;
    private String contentType;
    private Long size;
    private java.time.LocalDateTime uploadedAt;
    private String url;
}
