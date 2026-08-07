package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.dto.*;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.service.AuthService;
import com.wydad.digital.auth.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.refreshToken(request, ip, userAgent));
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
                user.isKycVerified(),
                user.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upgrade")
    public ResponseEntity<AuthResponse> upgradeLevel(@Valid @RequestBody UpgradeRequest request) {
        return ResponseEntity.ok(authService.upgradeLevel(request));
    }

    @GetMapping("/membership-status")
    public ResponseEntity<MembershipStatusResponse> checkMembershipStatus(
            @RequestParam("email") String email) {
        return ResponseEntity.ok(authService.checkMembershipStatus(email));
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
    public ResponseEntity<KycResponse> uploadKyc(@Valid @RequestBody KycUploadRequest request) {
        return ResponseEntity.ok(authService.uploadKyc(request));
    }

    @PostMapping("/kyc/verify")
    public ResponseEntity<KycResponse> verifyKyc(@RequestParam("email") String email) {
        return ResponseEntity.ok(authService.verifyKyc(email));
    }

    // ============================================
    // NOUVEAU : Sessions Actives
    // ============================================
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        return ResponseEntity.ok(authService.getActiveSessions(email, token));
    }

    @PostMapping("/sessions/revoke")
    public ResponseEntity<String> revokeSession(
            @Valid @RequestBody RevokeSessionRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        authService.revokeSession(request.sessionId(), email);
        return ResponseEntity.ok("Session révoquée avec succès");
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<String> revokeAllSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        authService.revokeAllSessions(email, token);
        return ResponseEntity.ok("Toutes les autres sessions ont été révoquées");
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}