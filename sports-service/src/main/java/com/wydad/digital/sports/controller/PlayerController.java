package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.PlayerDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.service.PlayerService;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final com.wydad.digital.sports.service.TeamIsolationService teamIsolationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerDto> createOrUpdatePlayer(@Valid @RequestBody PlayerDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playerService.createOrUpdatePlayer(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerDto> updatePlayer(@PathVariable Long id, @Valid @RequestBody PlayerDto dto) {
        return ResponseEntity.ok(playerService.updatePlayer(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePlayer(@PathVariable Long id) {
        playerService.deletePlayer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('JOUEUR') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<PlayerDto> getPlayerByUserId(@PathVariable Long userId) {
        // Anti-IDOR : un joueur ne peut consulter que sa propre fiche sportive ;
        // STAFF et ADMIN peuvent lire toutes les fiches.
        if (!SportsUserContext.isAdmin()
                && !"STAFF".equals(SportsUserContext.getCurrentUserRole())
                && !userId.equals(SportsUserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Consultation de la fiche d'un autre joueur interdite");
        }
        return ResponseEntity.ok(playerService.getPlayerByUserId(userId));
    }

    /**
     * Liste complète : back-office ADMIN et espace PRESIDENT uniquement
     * (§6 — l'encadrement et les joueurs passent par /filter, bornés à leur
     * propre groupe).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRESIDENT')")
    public ResponseEntity<List<PlayerDto>> getAllPlayers() {
        return ResponseEntity.ok(playerService.getAllPlayers());
    }

    /**
     * Listing par équipe avec isolation serveur (§6/§24) : le couple
     * discipline+catégorie est vérifié contre le profil de l'appelant — un
     * entraîneur Football U17 qui demande Basketball U17 reçoit 403.
     *
     * <p>C.21 — catégorie optionnelle pour le rôle PRESIDENT : le président
     * gère TOUTE sa discipline (Football U15 + U17 + U18 + U20 + Senior)
     * sans distinction de catégorie. Sans catégorie → toutes les catégories
     * de la discipline. Avec catégorie → filtrage classique (entraîneur).</p>
     */
    @GetMapping("/filter")
    @PreAuthorize("hasRole('ENTRAINEUR') or hasRole('STAFF') or hasRole('JOUEUR') "
            + "or hasRole('ADMIN') or hasRole('PRESIDENT')")
    public ResponseEntity<List<PlayerDto>> getPlayersByCategory(
            @RequestParam SportType sportType,
            @RequestParam(required = false) Category category) {
        // C.21 — un président qui omet la catégorie récupère toute sa
        // discipline (toutes catégories). L'isolation serveur vérifie que
        // le sportType demandé correspond à SA discipline de président.
        if (category == null) {
            teamIsolationService.ensureCanQueryDiscipline(sportType);
            return ResponseEntity.ok(playerService.getPlayersByDiscipline(sportType));
        }
        teamIsolationService.ensureCanQueryTeam(sportType, category);
        return ResponseEntity.ok(playerService.getPlayersByCategory(sportType, category));
    }
}
