package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.dto.SessionDtos.MyConvokedSession;
import com.wydad.digital.sports.dto.SessionDtos.SessionWithPlayersResponse;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.service.SessionConvocationService;
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
    private final SessionConvocationService sessionConvocationService;
    private final com.wydad.digital.sports.service.TeamIsolationService teamIsolationService;
    private final com.wydad.digital.sports.repository.StaffRepository staffRepository;

    /**
     * Création d'une séance par l'entraîneur (rôle ENTRAINEUR ou STAFF —
     * l'ADMIN peut aussi créer pour n'importe quel groupe). La liste des
     * joueurs convoqués (champ {@code joueurUserIds}) est obligatoire : le
     * service vérifie qu'ils appartiennent au groupe (anti-IDOR) et envoie
     * une notification in-app personnalisée à chacun.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
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
     * Vue ADMIN/PRÉSIDENT : séances d'un groupe enrichies de la liste des
     * joueurs convoqués (lecture seule). L'ADMIN voit tous les groupes ;
     * le PRÉSIDENT voit les groupes de sa discipline (filtrage serveur).
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','PRESIDENT')")
    public ResponseEntity<List<SessionWithPlayersResponse>> getSessionsForAdmin(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        return ResponseEntity.ok(
                sessionConvocationService.getSessionsForAdmin(sportType, category));
    }

    /**
     * Vue JOUEUR : séances où je suis convoqué. Filtré côté service par
     * le userId du contexte (anti-IDOR par construction).
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<List<MyConvokedSession>> getMyConvokedSessions() {
        return ResponseEntity.ok(
                sessionConvocationService.getMyConvokedSessions(
                        SportsUserContext.getCurrentUserId()));
    }

    /**
     * Anti-IDOR : un membre de l'encadrement ne peut consulter que ses propres
     * séances ; ADMIN et PRESIDENT peuvent lire celles de n'importe qui.
     */
    @GetMapping("/staff/{staffId}")
    @PreAuthorize("hasRole('ENTRAINEUR') or hasRole('STAFF') or hasRole('ADMIN') "
            + "or hasRole('PRESIDENT')")
    public ResponseEntity<List<SessionDto>> getSessionsByStaff(@PathVariable Long staffId) {
        if (!SportsUserContext.isAdmin()
                && !"PRESIDENT".equals(SportsUserContext.getCurrentUserRole())) {
            com.wydad.digital.sports.model.Staff own = staffRepository
                    .findByUserId(SportsUserContext.getCurrentUserId())
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
