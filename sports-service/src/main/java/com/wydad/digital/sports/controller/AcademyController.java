package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.AcademyMemberDto;
import com.wydad.digital.sports.service.AcademyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports/academy")
@RequiredArgsConstructor
public class AcademyController {

    private final AcademyService academyService;

    @PostMapping("/register")
    public ResponseEntity<AcademyMemberDto> registerChild(@Valid @RequestBody AcademyMemberDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academyService.registerChild(dto));
    }

    @GetMapping("/parent/{parentUserId}")
    public ResponseEntity<List<AcademyMemberDto>> getChildrenByParent(@PathVariable Long parentUserId) {
        return ResponseEntity.ok(academyService.getChildrenByParent(parentUserId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AcademyMemberDto> updateStatus(@PathVariable Long id, @RequestParam Boolean active) {
        return ResponseEntity.ok(academyService.updateChildStatus(id, active));
    }
}
