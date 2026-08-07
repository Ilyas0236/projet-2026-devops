package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.dto.AuthResponse;
import com.wydad.digital.auth.dto.LoginRequest;
import com.wydad.digital.auth.dto.MemberCardResponse;
import com.wydad.digital.auth.dto.RefreshTokenRequest;
import com.wydad.digital.auth.dto.RegisterRequest;
import com.wydad.digital.auth.service.AuthService;
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
}