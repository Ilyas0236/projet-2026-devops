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

    @GetMapping
    public ResponseEntity<List<PlayerDto>> getAllPlayers() {
        return ResponseEntity.ok(playerService.getAllPlayers());
    }

    @GetMapping("/filter")
    public ResponseEntity<List<PlayerDto>> getPlayersByCategory(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        return ResponseEntity.ok(playerService.getPlayersByCategory(sportType, category));
    }
}
