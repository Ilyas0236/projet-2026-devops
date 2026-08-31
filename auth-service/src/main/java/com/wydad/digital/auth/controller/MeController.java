package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * B.17 — Endpoints "mon compte" (utilisateur authentifié, tout rôle).
 *   - POST /api/auth/me/photo  : upload / remplacement de la photo de profil
 *
 * La photo sert principalement aux journalistes (badge d'accréditation),
 * mais l'endpoint est ouvert à tout utilisateur authentifié : on prépare
 * l'extension aux autres rôles (avatar espace membre).
 *
 * Garde de sécurité : email + role injectés par la gateway via X-User-* ;
 * le userId est résolu côté serveur à partir de l'email pour éviter
 * qu'un client n'uploade dans le folder d'un autre utilisateur.
 */
@RestController
@RequestMapping("/api/auth/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @PostMapping(value = "/photo", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadPhoto(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam("photo") MultipartFile photo) {
        if (email == null || role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            CloudinaryService.UploadResult r = cloudinaryService.uploadProfilePhoto(photo, user.getId());
            // On stocke l'URL publique (ou null en mode dégradé) sur le user.
            // Le frontend affichera une icône par défaut si l'URL est nulle.
            user.setPhotoUrl(r.secureUrl());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                    "photoUrl", r.secureUrl() != null ? r.secureUrl() : "",
                    "mode", r.cloud() ? "cloudinary" : "degraded"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Échec de l'upload de la photo"));
        }
    }
}
