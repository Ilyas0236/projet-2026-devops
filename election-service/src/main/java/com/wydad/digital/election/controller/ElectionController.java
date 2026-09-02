package com.wydad.digital.election.controller;

import com.wydad.digital.election.dto.ElectionDtos.AddCandidateRequest;
import com.wydad.digital.election.dto.ElectionDtos.CreateElectionRequest;
import com.wydad.digital.election.dto.ElectionDtos.ElectionView;
import com.wydad.digital.election.dto.ElectionDtos.UpdateElectionRequest;
import com.wydad.digital.election.service.ElectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Élections du président du club.
 *
 * Matrice rôles × routes (ISTQB table de décision) :
 *   - GET  /published, /published/latest : PUBLIC (site officiel, sans connexion)
 *   - GET  /open, /{id}                  : membres authentifiés (espace adhérent)
 *   - POST /{id}/vote                    : membres authentifiés (1 vote max)
 *   - POST create, candidats, close      : ADMIN uniquement
 *
 * L'identité du votant vient TOUJOURS du contexte JWT transmis par la
 * passerelle — jamais du corps de requête.
 */
@RestController
@RequestMapping("/api/elections")
@RequiredArgsConstructor
public class ElectionController {

    private final ElectionService electionService;

    /** Site public — visible aux visiteurs non connectés. */
    @GetMapping("/published")
    public ResponseEntity<List<ElectionView>> getPublished() {
        return ResponseEntity.ok(electionService.getPublished());
    }

    @GetMapping("/published/latest")
    public ResponseEntity<ElectionView> getLatestPublished() {
        ElectionView latest = electionService.getLatestPublished();
        return latest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(latest);
    }

    /** Espace adhérent — élections en cours avec l'état de vote de l'utilisateur. */
    @GetMapping("/open")
    public ResponseEntity<List<ElectionView>> getOpenElections() {
        return ResponseEntity.ok(electionService.getOpenForCurrentUser());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ElectionView> get(@PathVariable Long id) {
        return ResponseEntity.ok(electionService.get(id));
    }

    @PostMapping("/{id}/vote")
    @PreAuthorize("hasAnyRole('VISITEUR','ADHERENT','PARENT','JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','ADMIN')")
    public ResponseEntity<ElectionView> vote(@PathVariable Long id,
                                             @RequestBody Map<String, Long> body) {
        Long candidateId = body == null ? null : body.get("candidateId");
        if (candidateId == null) {
            throw new IllegalArgumentException("candidateId est obligatoire");
        }
        return ResponseEntity.ok(electionService.vote(id, candidateId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionView> create(@Valid @RequestBody CreateElectionRequest request) {
        String email = com.wydad.digital.election.filter.UserContext.getCurrentUserEmail();
        return ResponseEntity.status(HttpStatus.CREATED).body(electionService.create(request, email));
    }

    /**
     * B.8 — Vue ADMIN : toutes les élections (tous statuts), plus récente
     * d'abord. Indispensable pour pouvoir gérer (retirer un candidat
     * oublié, republier, dépublier…) les élections clôturées ou publiées
     * qui ont disparu de {@link #getOpenElections()}.
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ElectionView>> listAllForAdmin() {
        return ResponseEntity.ok(electionService.listAllForAdmin());
    }

    /**
     * B.8.b — Suppression d'une élection (uniquement si 0 vote).
     * Pour les élections clôturées/publiées sans vote (scrutins de
     * test par exemple), l'admin peut faire le ménage. Renvoie 204.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        electionService.deleteForAdmin(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * B.8.b — Modification d'une élection (titre + dates).
     * Refusé si status != OPEN ou si votes > 0.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionView> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateElectionRequest request) {
        return ResponseEntity.ok(electionService.updateForAdmin(id,
                request.getTitle(), request.getStartsAt(), request.getEndsAt()));
    }

    /**
     * B.8.b — Dépublication d'une élection (annule un publishResults).
     * Ramène published=false sans toucher au statut (reste CLOSED) ni
     * au winner déjà calculé. Utile si l'admin a publié par erreur.
     */
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionView> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(electionService.unpublishForAdmin(id));
    }

    @PostMapping("/{id}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionView> addCandidate(@PathVariable Long id,
                                                     @Valid @RequestBody AddCandidateRequest request) {
        return ResponseEntity.ok(electionService.addCandidate(id, request));
    }

    @DeleteMapping("/{id}/candidates/{candidateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionView> removeCandidate(@PathVariable Long id,
                                                        @PathVariable Long candidateId) {
        return ResponseEntity.ok(electionService.removeCandidate(id, candidateId));
    }

    /**
     * B.8 — Clôture seule (gèle, ne publie PAS). Pour publier
     * explicitement, utiliser POST /{id}/publish (avec garde
     * "tous les titulaires ont voté").
     *
     * <p>BREAKING : l'ancien comportement couplé (close+publish) est
     * préservé uniquement en interne (cf. closeAndPublish()) pour
     * rétro-compat, mais le front doit désormais utiliser /close puis
     * /publish en deux temps.</p>
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionView> closeOnly(@PathVariable Long id) {
        return ResponseEntity.ok(electionService.closeOnly(id));
    }

    /**
     * B.8 — Publication explicite des résultats. 409 NOT_ALL_VOTED
     * si pas tous les titulaires ont voté. Idempotent.
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionView> publishResults(@PathVariable Long id) {
        return ResponseEntity.ok(electionService.publishResults(id));
    }

    /**
     * B.8 — État d'éligibilité à la publication. Le front admin
     * l'interroge pour griser/dégriser le bouton "Publier" et
     * afficher l'indicateur "X/Y ont voté".
     */
    @GetMapping("/{id}/publish-eligibility")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionService.PublishEligibility> getPublishEligibility(@PathVariable Long id) {
        return ResponseEntity.ok(electionService.getPublishEligibility(id));
    }
}
