package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.dto.StaffDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final com.wydad.digital.sports.service.TeamIsolationService teamIsolationService;

    /** Liste complete pour le back-office ADMIN. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StaffDto>> getAllStaff() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffDto> createOrUpdateStaff(@Valid @RequestBody StaffDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createOrUpdateStaff(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffDto> updateStaff(@PathVariable Long id, @Valid @RequestBody StaffDto dto) {
        return ResponseEntity.ok(staffService.updateStaff(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStaff(@PathVariable Long id) {
        staffService.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }

    /** Listing par équipe avec isolation serveur (§6/§24). */
    @GetMapping("/filter")
    @PreAuthorize("hasRole('ENTRAINEUR') or hasRole('STAFF') or hasRole('JOUEUR') "
            + "or hasRole('ADMIN') or hasRole('PRESIDENT')")
    public ResponseEntity<List<StaffDto>> getStaffByTeam(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        teamIsolationService.ensureCanQueryTeam(sportType, category);
        return ResponseEntity.ok(staffService.getStaffByTeam(sportType, category));
    }

    /** Lecture d'un profil staff par userId : ADMIN/PRESIDENT voient tout,
     * un STAFF/ENTRAINEUR ne peut voir que SON propre profil (anti-énumération). */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRESIDENT') or hasRole('ENTRAINEUR') or hasRole('STAFF')")
    public ResponseEntity<StaffDto> getStaffByUserId(@PathVariable Long userId) {
        // Anti-énumération : un STAFF/ENTRAINEUR ne peut consulter que son propre profil
        // (ADMIN/PRESIDENT passent le filtre ci-dessous)
        boolean isAdminOrPresident = com.wydad.digital.sports.filter.SportsUserContext.isAdmin()
                || com.wydad.digital.sports.filter.SportsUserContext.isPresident();
        if (!isAdminOrPresident) {
            Long currentUserId = com.wydad.digital.sports.filter.SportsUserContext.getCurrentUserId();
            if (currentUserId == null || !currentUserId.equals(userId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Vous ne pouvez consulter que votre propre profil staff");
            }
        }
        return ResponseEntity.ok(staffService.getStaffByUserId(userId));
    }

    // Sessions are managed by staff
    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','ENTRAINEUR')")
    public ResponseEntity<SessionDto> createSession(@Valid @RequestBody SessionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createSession(dto));
    }

    @GetMapping("/sessions/filter")
    @PreAuthorize("hasRole('ENTRAINEUR') or hasRole('STAFF') or hasRole('JOUEUR') "
            + "or hasRole('ADMIN') or hasRole('PRESIDENT')")
    public ResponseEntity<List<SessionDto>> getTeamSessions(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        teamIsolationService.ensureCanQueryTeam(sportType, category);
        return ResponseEntity.ok(staffService.getTeamSessions(sportType, category));
    }
}
