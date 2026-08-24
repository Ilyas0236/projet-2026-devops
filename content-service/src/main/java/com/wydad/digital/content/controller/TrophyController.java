package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.Trophy;
import com.wydad.digital.content.service.TrophyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Palmarès du club. Lecture publique (page « Palmarès »), écriture ADMIN
 * uniquement — même convention que SponsorController.
 */
@RestController
@RequestMapping("/api/content/trophies")
@RequiredArgsConstructor
public class TrophyController {

    private final TrophyService trophyService;

    /** Public : trophées actifs, ordonnés. */
    @GetMapping("/public")
    public ResponseEntity<List<Trophy>> getPublicTrophies() {
        return ResponseEntity.ok(trophyService.getPublicTrophies());
    }

    /** Admin : tous les trophées (y compris inactifs). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Trophy>> getAllTrophies() {
        return ResponseEntity.ok(trophyService.getAllTrophies());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Trophy> create(@RequestBody TrophyRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trophyService.create(body.title(), body.category(), body.season(),
                        body.count(), body.imageUrl(), body.displayOrder()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Trophy> update(@PathVariable Long id, @RequestBody TrophyRequest body) {
        return ResponseEntity.ok(trophyService.update(id, body.title(), body.category(),
                body.season(), body.count(), body.imageUrl(), body.displayOrder(), body.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        trophyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record TrophyRequest(
            String title,
            String category,
            String season,
            Integer count,
            String imageUrl,
            Integer displayOrder,
            Boolean active) {
    }
}
