package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.Media;
import com.wydad.digital.content.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/content/media")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class MediaController {

    private final MediaRepository mediaRepository;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
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

    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> getFile(@PathVariable String fileName) {
        return mediaRepository.findByFileName(fileName)
                .map(media -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType(media.getContentType()));
                    headers.setContentLength(media.getSize());
                    headers.setCacheControl("public, max-age=31536000");
                    return new ResponseEntity<>(media.getData(), headers, HttpStatus.OK);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        mediaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
