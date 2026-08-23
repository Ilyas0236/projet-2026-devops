package com.wydad.digital.content.controller;

import com.wydad.digital.content.dto.MediaResponse;
import com.wydad.digital.content.model.Media;
import com.wydad.digital.content.repository.MediaRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/content/media")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class MediaController {

    private final MediaRepository mediaRepository;

    /** Types de fichiers autorisés à l'upload (images et PDF uniquement). */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"
    );

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        // Whitelist MIME : empêche l'upload de fichiers exécutables/HTML via le CMS
        if (file.isEmpty() || file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

            Media media = Media.builder()
                    .fileName(fileName)
                    .originalName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .data(file.getBytes())
                    .build();

            mediaRepository.save(media);

            Map<String, String> response = new HashMap<>();
            response.put("url", "/api/content/media/" + fileName);
            response.put("fileName", fileName);
            response.put("originalName", file.getOriginalFilename());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
