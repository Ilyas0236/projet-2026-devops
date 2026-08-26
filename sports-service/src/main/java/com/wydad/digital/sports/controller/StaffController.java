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

    /** Lecture d'un profil staff par userId : réservé à l'encadrement et au
     * back-office (anti-IDOR : un JOUEUR ne peut pas sonder les profils staff). */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ENTRAINEUR') or hasRole('STAFF') or hasRole('ADMIN') "
            + "or hasRole('PRESIDENT')")
    public ResponseEntity<StaffDto> getStaffByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(staffService.getStaffByUserId(userId));
    }

    // Sessions are managed by staff
    @PostMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
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
