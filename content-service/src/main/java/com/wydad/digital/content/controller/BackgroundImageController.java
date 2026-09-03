package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.BackgroundImage;
import com.wydad.digital.content.service.BackgroundImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.7 + admin-parametres : images de fond / hero multi-photo par page vitrine.
 * <ul>
 *   <li>{@code GET /public?pageKey=HOME} — anonyme, renvoie les backgrounds actifs</li>
 *   <li>{@code GET /admin} — ADMIN, tous les backgrounds (actifs + inactifs)</li>
 *   <li>{@code POST /admin} — ADMIN, crée un background (imageUrl déjà uploadée via /media/upload)</li>
 *   <li>{@code PUT /admin/{id}} — ADMIN, modifie caption/displayOrder/active</li>
 *   <li>{@code DELETE /admin/{id}} — ADMIN, supprime</li>
 * </ul>
 * Pas de {@code @CrossOrigin} : CORS via la gateway (cf. MediaController).
 */
@RestController
@RequestMapping("/api/content/backgrounds")
@RequiredArgsConstructor
public class BackgroundImageController {

    private final BackgroundImageService service;

    @GetMapping("/public")
    public ResponseEntity<List<BackgroundImage>> getPublic(@RequestParam String pageKey) {
        return ResponseEntity.ok(service.getPublicForPage(pageKey));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BackgroundImage>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackgroundImage> create(@RequestBody BackgroundRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(body.pageKey(), body.imageUrl(),
                        body.caption(), body.displayOrder()));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackgroundImage> update(@PathVariable Long id, @RequestBody BackgroundUpdateRequest body) {
        return ResponseEntity.ok(service.update(id, body.caption(), body.displayOrder(), body.active()));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record BackgroundRequest(
            String pageKey,
            String imageUrl,
            String caption,
            Integer displayOrder) {
    }

    public record BackgroundUpdateRequest(
            String caption,
            Integer displayOrder,
            Boolean active) {
    }
}
