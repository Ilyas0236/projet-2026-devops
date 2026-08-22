package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.dto.*;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.service.AuthService;
import com.wydad.digital.auth.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.login(request, ip, userAgent));
    }

    @GetMapping("/member-card")
    public ResponseEntity<MemberCardResponse> getMemberCard(
            @RequestParam("email") String email,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {

        // Un utilisateur ne peut consulter que sa propre carte, sauf l'admin
        boolean isAdmin = "ADMIN".equals(gatewayRole);
        if (!isAdmin && gatewayEmail != null && !gatewayEmail.equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(authService.getMemberCard(email));
    }

    @GetMapping("/attestation")
    public ResponseEntity<byte[]> generateAttestation(
            @RequestParam("email") String email,
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {

        // Un utilisateur ne peut générer que sa propre attestation, sauf l'admin
        boolean isAdmin = "ADMIN".equals(gatewayRole);
        if (!isAdmin && gatewayEmail != null && !gatewayEmail.equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] pdf = authService.generateAttestation(email);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attestation-wac.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.refreshToken(request, ip, userAgent));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
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
                user.isKycVerified(),
                user.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<AuthResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        return ResponseEntity.ok(authService.updateProfile(request));
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<String> deleteAccount(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        authService.deleteAccount(email);
        return ResponseEntity.ok("Compte supprimé avec succès");
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<AuthResponse> upgradeLevel(@Valid @RequestBody UpgradeRequest request) {
        return ResponseEntity.ok(authService.upgradeLevel(request));
    }

    @GetMapping("/membership-status")
    public ResponseEntity<MembershipStatusResponse> checkMembershipStatus(
            @RequestParam(value = "email", required = false) String email,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        // Un utilisateur ne peut consulter que son propre statut ; seul l'ADMIN
        // peut interroger le statut d'un autre membre.
        String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        String currentEmail = token != null ? jwtUtils.getEmailFromToken(token) : null;
        boolean isAdmin = token != null && "ADMIN".equals(jwtUtils.getRoleFromToken(token));

        if (!isAdmin && email != null && !email.isBlank() && !email.equals(currentEmail)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Consultation du statut d'un autre membre interdite");
        }
        String targetEmail = (isAdmin && email != null && !email.isBlank()) ? email : currentEmail;
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Email cible requis");
        }
        return ResponseEntity.ok(authService.checkMembershipStatus(targetEmail));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<String> sendOtp(@Valid @RequestBody OtpRequest request) {
        String code = authService.sendOtp(request);
        return ResponseEntity.ok("Code OTP généré (mock): " + code);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        boolean valid = authService.verifyOtp(request);
        return valid
                ? ResponseEntity.ok("OTP validé avec succès")
                : ResponseEntity.badRequest().body("OTP invalide ou expiré");
    }

    @PostMapping("/kyc/upload")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<KycResponse> uploadKyc(@Valid @RequestBody KycUploadRequest request) {
        return ResponseEntity.ok(authService.uploadKyc(request));
    }

    @PostMapping("/kyc/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycResponse> verifyKyc(@RequestParam("email") String email) {
        return ResponseEntity.ok(authService.verifyKyc(email));
    }

    // ============================================
    // Sessions Actives
    // ============================================
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        return ResponseEntity.ok(authService.getActiveSessions(email, token));
    }

    @PostMapping("/sessions/revoke")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<String> revokeSession(
            @Valid @RequestBody RevokeSessionRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        authService.revokeSession(request.sessionId(), email);
        return ResponseEntity.ok("Session révoquée avec succès");
    }

    @PostMapping("/sessions/revoke-all")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<String> revokeAllSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        authService.revokeAllSessions(email, token);
        return ResponseEntity.ok("Toutes les autres sessions ont été révoquées");
    }

    // ============================================
    // ADMIN ENDPOINTS
    // ============================================
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileResponse>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @PatchMapping("/admin/users/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> toggleUserActiveStatus(@PathVariable Long id, @RequestParam boolean status) {
        authService.toggleUserActiveStatus(id, status);
        return ResponseEntity.ok("Statut utilisateur mis à jour");
    }

    @PatchMapping("/admin/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> changeUserRole(@PathVariable Long id, @RequestParam String newRole) {
        authService.changeUserRole(id, newRole);
        return ResponseEntity.ok("Rôle utilisateur mis à jour");
    }

    @PostMapping("/admin/users/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> adminCreateUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.adminCreateUser(request));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}