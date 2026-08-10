package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.PlayerDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @PostMapping
    public ResponseEntity<PlayerDto> createOrUpdatePlayer(@Valid @RequestBody PlayerDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playerService.createOrUpdatePlayer(dto));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PlayerDto> getPlayerByUserId(@PathVariable Long userId) {
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
