package com.wydad.digital.content.controller;

import com.wydad.digital.content.model.ClubSetting;
import com.wydad.digital.content.service.ClubSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Parametres du club (paliers d'adhesion, coordonnees...).
 * Lecture publique, ecriture ADMIN uniquement.
 */
@RestController
@RequestMapping("/api/content/settings")
@RequiredArgsConstructor
public class ClubSettingController {

    private final ClubSettingService clubSettingService;

    @GetMapping
    public ResponseEntity<List<ClubSetting>> getAllSettings() {
        return ResponseEntity.ok(clubSettingService.getAllSettings());
    }

    @GetMapping("/{key}")
    public ResponseEntity<Object> getSetting(@PathVariable String key) {
        Object value = clubSettingService.getSetting(key);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(value);
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClubSetting> upsertSetting(@PathVariable String key, @RequestBody Object value) {
        return ResponseEntity.ok(clubSettingService.upsertSetting(key, value));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSetting(@PathVariable String key) {
        clubSettingService.deleteSetting(key);
        return ResponseEntity.noContent().build();
    }
}
