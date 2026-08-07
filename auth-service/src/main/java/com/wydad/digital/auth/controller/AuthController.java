package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.dto.*;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.service.AuthService;
import com.wydad.digital.auth.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/member-card")
    public ResponseEntity<MemberCardResponse> getMemberCard(@RequestParam("email") String email) {
        return ResponseEntity.ok(authService.getMemberCard(email));
    }

    @GetMapping("/attestation")
    public ResponseEntity<byte[]> generateAttestation(@RequestParam("email") String email) {
        byte[] pdf = authService.generateAttestation(email);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attestation-wac.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        User user = authService.getUserByEmail(email);

        UserProfileResponse response = new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getRole(),
                user.getMembershipExpiresAt(),
                user.getReferralCode(),
                user.isActive(),
                user.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    // ============================================
    // NOUVEAU : Upgrade niveau d'adhésion
    // ============================================
    @PostMapping("/upgrade")
    public ResponseEntity<AuthResponse> upgradeLevel(@Valid @RequestBody UpgradeRequest request) {
        return ResponseEntity.ok(authService.upgradeLevel(request));
    }

    // ============================================
    // NOUVEAU : Vérifier statut expiration
    // ============================================
    @GetMapping("/membership-status")
    public ResponseEntity<MembershipStatusResponse> checkMembershipStatus(
            @RequestParam("email") String email) {
        return ResponseEntity.ok(authService.checkMembershipStatus(email));
    }
}