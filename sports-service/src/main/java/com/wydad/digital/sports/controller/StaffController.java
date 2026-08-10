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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    public ResponseEntity<StaffDto> createOrUpdateStaff(@Valid @RequestBody StaffDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createOrUpdateStaff(dto));
    }

    @GetMapping("/filter")
    public ResponseEntity<List<StaffDto>> getStaffByTeam(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        return ResponseEntity.ok(staffService.getStaffByTeam(sportType, category));
    }

    // Sessions are managed by staff
    @PostMapping("/sessions")
    public ResponseEntity<SessionDto> createSession(@Valid @RequestBody SessionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createSession(dto));
    }

    @GetMapping("/sessions/filter")
    public ResponseEntity<List<SessionDto>> getTeamSessions(
            @RequestParam SportType sportType,
            @RequestParam Category category) {
        return ResponseEntity.ok(staffService.getTeamSessions(sportType, category));
    }
}
