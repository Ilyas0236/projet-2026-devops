package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.Sponsor;
import com.wydad.digital.content.service.SponsorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B.7 — Sponsors du club. Lecture publique (page vitrine, espace membre),
 * écriture ADMIN uniquement.
 */
@RestController
@RequestMapping("/api/content/sponsors")
@RequiredArgsConstructor
public class SponsorController {

    private final SponsorService sponsorService;

    /** Public : sponsors actifs, ordonnés. */
    @GetMapping("/public")
    public ResponseEntity<List<Sponsor>> getPublicSponsors() {
        return ResponseEntity.ok(sponsorService.getPublicSponsors());
    }

    /** Admin : tous les sponsors (y compris inactifs). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Sponsor>> getAllSponsors() {
        return ResponseEntity.ok(sponsorService.getAllSponsors());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Sponsor> create(@RequestBody SponsorRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sponsorService.create(body.name(), body.logoUrl(), body.websiteUrl(),
                        body.tier(), body.displayOrder()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Sponsor> update(@PathVariable Long id, @RequestBody SponsorRequest body) {
        return ResponseEntity.ok(sponsorService.update(id, body.name(), body.logoUrl(),
                body.websiteUrl(), body.tier(), body.displayOrder(), body.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sponsorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record SponsorRequest(
            String name,
            String logoUrl,
            String websiteUrl,
            String tier,
            Integer displayOrder,
            Boolean active) {
    }
}
