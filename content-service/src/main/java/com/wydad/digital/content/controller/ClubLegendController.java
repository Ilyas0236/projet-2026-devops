package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.ClubLegend;
import com.wydad.digital.content.service.ClubLegendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hall of Fame du club. Lecture publique (page « Légendes »), écriture
 * ADMIN uniquement — même convention que TrophyController.
 */
@RestController
@RequestMapping("/api/content/legends")
@RequiredArgsConstructor
public class ClubLegendController {

    private final ClubLegendService legendService;

    /** Public : légendes actives, ordonnées. */
    @GetMapping("/public")
    public ResponseEntity<List<ClubLegend>> getPublicLegends() {
        return ResponseEntity.ok(legendService.getPublicLegends());
    }

    /** Admin : toutes les légendes (y compris inactives). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClubLegend>> getAllLegends() {
        return ResponseEntity.ok(legendService.getAllLegends());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClubLegend> create(@RequestBody LegendRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(legendService.create(body.name(), body.nickname(), body.role(),
                        body.yearFrom(), body.yearTo(), body.biography(),
                        body.imageUrl(), body.displayOrder()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClubLegend> update(@PathVariable Long id, @RequestBody LegendRequest body) {
        return ResponseEntity.ok(legendService.update(id, body.name(), body.nickname(),
                body.role(), body.yearFrom(), body.yearTo(), body.biography(),
                body.imageUrl(), body.displayOrder(), body.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        legendService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record LegendRequest(
            String name,
            String nickname,
            String role,
            Integer yearFrom,
            Integer yearTo,
            String biography,
            String imageUrl,
            Integer displayOrder,
            Boolean active) {
    }
}
