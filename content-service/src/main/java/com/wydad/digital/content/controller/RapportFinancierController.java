package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.RapportFinancier;
import com.wydad.digital.content.repository.RapportFinancierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Rapports financiers du club (Phase transparence).
 * Lecture : publique (page /transparence) — le rapport d'un club sportif
 * est une pièce de transparence ; l'écriture est réservée à l'ADMIN.
 * L'envoi de la notification aux adhérents est déclenché par le front via
 * POST /api/notification/broadcast après publication.
 */
@RestController
@RequestMapping("/api/content/rapports-financiers")
@RequiredArgsConstructor
public class RapportFinancierController {

    private final RapportFinancierRepository repository;

    @GetMapping
    public ResponseEntity<List<RapportFinancier>> getAll() {
        return ResponseEntity.ok(repository.findAllByOrderByAnneeDescPublieLeDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RapportFinancier> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Publication : le front a déjà uploadé le PDF dans la médiathèque. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RapportFinancier> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        try {
            String fileUrl = (String) body.get("fileUrl");
            String titre = (String) body.get("titre");
            Integer annee = body.get("annee") != null ? ((Number) body.get("annee")).intValue() : null;
            if (fileUrl == null || fileUrl.isBlank() || titre == null || titre.isBlank() || annee == null) {
                return ResponseEntity.badRequest().build();
            }
            RapportFinancier rapport = RapportFinancier.builder()
                    .titre(titre.trim())
                    .annee(annee)
                    .description(body.get("description") != null ? ((String) body.get("description")).trim() : null)
                    .fileUrl(fileUrl)
                    .originalName(body.get("originalName") != null ? (String) body.get("originalName") : null)
                    .publiePar(adminEmail != null ? adminEmail : "admin")
                    .build();
            return ResponseEntity.ok(repository.save(rapport));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
