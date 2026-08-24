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
    private final com.wydad.digital.auth.config.InternalSecretValidator internalSecretValidator;

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

        // S5 (défense-en-profondeur) : la gateway exige le JWT et pose TOUJOURS
        // X-User-* ; si ces headers sont absents, la requête n'est pas passée
        // par elle (appel direct au port du service) → refus systématique,
        // même si l'email demandé correspondrait à un compte existant.
        if (gatewayEmail == null || gatewayRole == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Un utilisateur ne peut consulter que sa propre carte, sauf l'admin
        boolean isAdmin = "ADMIN".equals(gatewayRole);
        if (!isAdmin && !gatewayEmail.equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(authService.getMemberCard(email));
    }

    @GetMapping("/attestation")
    public ResponseEntity<byte[]> generateAttestation(
            @RequestParam("email") String email,
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {

        // S5 : mêmes garanties que member-card — headers d'identité obligatoires.
        if (gatewayEmail == null || gatewayRole == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Un utilisateur ne peut générer que sa propre attestation, sauf l'admin
        boolean isAdmin = "ADMIN".equals(gatewayRole);
        if (!isAdmin && !gatewayEmail.equalsIgnoreCase(email)) {
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
                user.getStatutCompte(),
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
        // IDOR : seul l'email dérivé du JWT fait foi ; un utilisateur ne peut
        // modifier que son propre profil (l'admin peut cibler un autre compte
        // en passant explicitement un email différent).
        String tokenEmail = jwtUtils.getEmailFromToken(token(authHeader));
        boolean isAdmin = "ADMIN".equals(jwtUtils.getRoleFromToken(token(authHeader)));
        UpdateProfileRequest effective = (!isAdmin && !tokenEmail.equalsIgnoreCase(request.email()))
                ? new UpdateProfileRequest(tokenEmail, request.firstName(), request.lastName(),
                        request.phone(), request.ville(), request.langue(), request.timezone(), request.bio())
                : request;
        return ResponseEntity.ok(authService.updateProfile(effective));
    }

    private String token(String authHeader) {
        return authHeader.replace("Bearer ", "");
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
    public ResponseEntity<AuthResponse> upgradeLevel(
            @Valid @RequestBody UpgradeRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        // IDOR : l'upgrade s'applique au compte du JWT ; seul l'ADMIN peut
        // upgrader un autre compte en le ciblant explicitement.
        String tokenEmail = jwtUtils.getEmailFromToken(token(authHeader));
        boolean isAdmin = "ADMIN".equals(jwtUtils.getRoleFromToken(token(authHeader)));
        UpgradeRequest effective = (!isAdmin && !tokenEmail.equalsIgnoreCase(request.email()))
                ? new UpgradeRequest(tokenEmail, request.newLevel())
                : request;
        return ResponseEntity.ok(authService.upgradeLevel(effective));
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
        // Le code n'est JAMAIS renvoyé dans la réponse HTTP : en production il
        // transiterait par SMS/email ; en démonstration, il est consultable
        // uniquement via GET /otp/mock-code si app.otp.mock-delivery=true.
        authService.sendOtp(request);
        return ResponseEntity.ok("Code OTP généré et envoyé");
    }

    /**
     * Canal de démonstration clairement isolé (app.otp.mock-delivery=true).
     * Désactivé par défaut — renvoie 404 sinon.
     */
    @GetMapping("/otp/mock-code")
    public ResponseEntity<String> getMockOtpCode(@RequestParam("email") String email) {
        String code = authService.getMockOtpCode(email);
        if (code == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(code);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        boolean valid = authService.verifyOtp(request);
        return valid
                ? ResponseEntity.ok("OTP validé avec succès")
                : ResponseEntity.badRequest().body("OTP invalide ou expiré");
    }

    /**
     * S6 : finalisation de la réinitialisation de mot de passe. Public
     * (l'utilisateur a perdu son mot de passe) mais protégé par l'OTP —
     * send → (reçoit le code) → reset. Les sessions actives sont coupées.
     */
    @PostMapping("/password/reset")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Mot de passe réinitialisé avec succès");
    }

    @PostMapping("/kyc/upload")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<KycResponse> uploadKyc(@Valid @RequestBody KycUploadRequest request) {
        return ResponseEntity.ok(authService.uploadKyc(request));
    }

    /**
     * Phase 1 — upload RÉEL du justificatif (multipart) : le fichier part vers
     * Cloudinary (folder privé), seuls publicId + URL sécurisée sont stockés.
     * L'utilisateur ne peut déposer que pour SON compte ; l'admin pour tous.
     */
    @PostMapping(value = "/kyc/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADHERENT') or hasRole('JOURNALISTE') or hasRole('ENTRAINEUR') or hasRole('ADMIN')")
    public ResponseEntity<KycResponse> uploadKycFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam("documentNumber") String documentNumber,
            @RequestHeader(value = "X-User-Email", required = false) String gatewayEmail,
            @RequestHeader(value = "X-User-Role", required = false) String gatewayRole,
            @RequestParam(value = "email", required = false) String targetEmail) {

        if (gatewayEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Un utilisateur ne dépose que pour son propre compte, sauf l'admin.
        String email = targetEmail != null && !targetEmail.isBlank() && "ADMIN".equals(gatewayRole)
                ? targetEmail
                : gatewayEmail;
        if (!"ADMIN".equals(gatewayRole) && !email.equalsIgnoreCase(gatewayEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(authService.uploadKycFile(file, email, documentType, documentNumber));
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

    // ============================================
    // Phase 0 — Circuit de validation des comptes
    // ============================================

    /** Liste des demandes de comptes en attente de validation. */
    @GetMapping("/admin/accounts/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileResponse>> getPendingAccounts() {
        return ResponseEntity.ok(authService.getPendingAccounts());
    }

    /** Valide une demande de compte (rôles privilégiés). */
    @PatchMapping("/admin/accounts/{id}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> validateAccount(@PathVariable Long id) {
        return ResponseEntity.ok(authService.validateAccount(id));
    }

    /** Refuse une demande de compte avec motif obligatoire. */
    @PatchMapping("/admin/accounts/{id}/refuse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> refuseAccount(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody
            @jakarta.validation.constraints.NotBlank String motif) {
        return ResponseEntity.ok(authService.refuseAccount(id, motif));
    }

    @PostMapping("/admin/users/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> adminCreateUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.adminCreateUser(request));
    }

    /**
     * Endpoint interne service-a-service (notification-service -> broadcast).
     * Protege par le secret partage X-Internal-Secret ; jamais expose via la
     * gateway (route bloquee cote gateway pour /api/auth/internal/**).
     */
    @GetMapping("/internal/recipients")
    public ResponseEntity<List<UserProfileResponse>> internalRecipients(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (!internalSecretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(authService.getAllActiveUsers());
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}