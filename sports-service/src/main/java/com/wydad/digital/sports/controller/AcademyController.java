package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.AcademyMemberDto;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.service.AcademyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports/academy")
@RequiredArgsConstructor
public class AcademyController {

    private final AcademyService academyService;

    /** Inscription d'un enfant : réservée aux PARENT (et ADMIN). */
    @PostMapping("/register")
    @PreAuthorize("hasRole('PARENT') or hasRole('ADMIN')")
    public ResponseEntity<AcademyMemberDto> registerChild(@Valid @RequestBody AcademyMemberDto dto) {
        // Un parent ne peut inscrire un enfant que pour lui-même (anti-IDOR)
        if (!SportsUserContext.isAdmin()
                && dto.getParentUserId() != null
                && !dto.getParentUserId().equals(SportsUserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Inscription pour un autre parent interdite");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(academyService.registerChild(dto));
    }

    @GetMapping("/parent/{parentUserId}")
    @PreAuthorize("hasRole('PARENT') or hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<AcademyMemberDto>> getChildrenByParent(@PathVariable Long parentUserId) {
        if (!SportsUserContext.isAdmin() && !parentUserId.equals(SportsUserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Accès aux enfants d'un autre parent interdit");
        }
        return ResponseEntity.ok(academyService.getChildrenByParent(parentUserId));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<AcademyMemberDto> updateStatus(@PathVariable Long id, @RequestParam Boolean active) {
        return ResponseEntity.ok(academyService.updateChildStatus(id, active));
    }
}
