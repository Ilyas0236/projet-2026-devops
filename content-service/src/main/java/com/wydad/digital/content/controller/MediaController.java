package com.wydad.digital.content.controller;

import com.wydad.digital.content.dto.MediaResponse;
import com.wydad.digital.content.model.Media;
import com.wydad.digital.content.repository.MediaRepository;
import com.wydad.digital.content.service.FileTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/content/media")
@RequiredArgsConstructor
// Pas de @CrossOrigin ici : le CORS est géré centralement par le CorsWebFilter
// de l'api-gateway (CORS_ALLOWED_ORIGINS). Une annotation locale avec un défaut
// localhost rejetait en 403 "Invalid CORS request" les POST du navigateur en
// production (Origin http://158.158.74.169:4200) alors que la gateway acceptait.
public class MediaController {

    private final MediaRepository mediaRepository;
    private final FileTypeValidator fileTypeValidator;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        // Validation du type REEL par magic bytes : le Content-Type declare par
        // le client est forgeable, un executable renomme en .jpg est rejete ici.
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (file.isEmpty() || file.getContentType() == null
                || !fileTypeValidator.isAllowed(file.getContentType(), data)) {
            return ResponseEntity.badRequest().build();
        }
        String fileName = fileTypeValidator.sanitizeFileName(file.getOriginalFilename());

        Media media = Media.builder()
                .fileName(fileName)
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .data(data)
                .build();

        mediaRepository.save(media);

        Map<String, String> response = new HashMap<>();
        response.put("url", "/api/content/media/" + fileName);
        response.put("fileName", fileName);
        response.put("originalName", file.getOriginalFilename());

        return ResponseEntity.ok(response);
    }

    /** Listing metadonnees (ADMIN) pour la mediatheque du back-office : sans les blobs. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MediaResponse>> listFiles() {
        List<MediaResponse> files = mediaRepository.findAll().stream()
                .map(m -> new MediaResponse(
                        m.getId(), m.getFileName(), m.getOriginalName(),
                        m.getContentType(), m.getSize(), m.getUploadedAt(),
                        "/api/content/media/" + m.getFileName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> getFile(@PathVariable String fileName) {
        return mediaRepository.findByFileName(fileName)
                .map(media -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType(media.getContentType()));
                    headers.setContentLength(media.getSize());
                    headers.setCacheControl("public, max-age=31536000");
                    headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
                    return new ResponseEntity<>(media.getData(), headers, HttpStatus.OK);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        mediaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
