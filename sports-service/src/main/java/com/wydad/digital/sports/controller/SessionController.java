package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final com.wydad.digital.sports.service.TeamIsolationService teamIsolationService;
    private final com.wydad.digital.sports.repository.StaffRepository staffRepository;

    @PostMapping
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<SessionDto> createSession(@Valid @RequestBody SessionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(dto));
    }

    /** Séances d'une équipe avec isolation serveur (§6/§24). */
    @GetMapping("/filter")
    @PreAuthorize("hasRole('ENTRAINEUR') or hasRole('STAFF') or hasRole('JOUEUR') "
            + "or hasRole('ADMIN') or hasRole('PRESIDENT')")
    public ResponseEntity<List<SessionDto>> getSessionsByCategory(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        teamIsolationService.ensureCanQueryTeam(sportType, category);
        return ResponseEntity.ok(sessionService.getSessionsByCategory(sportType, category));
    }

    /**
     * Anti-IDOR : un membre de l'encadrement ne peut consulter que ses propres
     * séances ; ADMIN et PRESIDENT peuvent lire celles de n'importe qui.
     */
    @GetMapping("/staff/{staffId}")
    @PreAuthorize("hasRole('ENTRAINEUR') or hasRole('STAFF') or hasRole('ADMIN') "
            + "or hasRole('PRESIDENT')")
    public ResponseEntity<List<SessionDto>> getSessionsByStaff(@PathVariable Long staffId) {
        if (!com.wydad.digital.sports.filter.SportsUserContext.isAdmin()
                && !"PRESIDENT".equals(com.wydad.digital.sports.filter.SportsUserContext.getCurrentUserRole())) {
            com.wydad.digital.sports.model.Staff own = staffRepository
                    .findByUserId(com.wydad.digital.sports.filter.SportsUserContext.getCurrentUserId())
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                            "Aucun profil encadrement rattaché à ce compte"));
            if (!own.getId().equals(staffId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Consultation des séances d'un autre encadrement interdite");
            }
        }
        return ResponseEntity.ok(sessionService.getSessionsByStaff(staffId));
    }
}
