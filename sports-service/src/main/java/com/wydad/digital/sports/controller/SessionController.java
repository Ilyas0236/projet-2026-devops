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

    @PostMapping
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<SessionDto> createSession(@Valid @RequestBody SessionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(dto));
    }

    @GetMapping("/filter")
    public ResponseEntity<List<SessionDto>> getSessionsByCategory(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        return ResponseEntity.ok(sessionService.getSessionsByCategory(sportType, category));
    }

    @GetMapping("/staff/{staffId}")
    public ResponseEntity<List<SessionDto>> getSessionsByStaff(@PathVariable Long staffId) {
        return ResponseEntity.ok(sessionService.getSessionsByStaff(staffId));
    }
}
