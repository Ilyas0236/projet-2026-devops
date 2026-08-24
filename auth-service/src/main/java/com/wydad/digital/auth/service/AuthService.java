package com.wydad.digital.auth.service;

import com.wydad.digital.auth.dto.*;
import com.wydad.digital.auth.exception.CompteNonValideException;
import com.wydad.digital.auth.exception.EmailAlreadyExistsException;
import com.wydad.digital.auth.exception.InvalidCredentialsException;
import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.ActiveSession;
import com.wydad.digital.auth.model.KycDocument;
import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.ActiveSessionRepository;
import com.wydad.digital.auth.repository.KycDocumentRepository;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final ActiveSessionRepository activeSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final QrCodeService qrCodeService;
    private final PdfService pdfService;
    private final OtpService otpService;
    private final CloudinaryService cloudinaryService;
    private final com.wydad.digital.auth.client.NotificationClient notificationClient;

    /** Niveau attribué à l'inscription : le plus bas payant (S3). */
    private static final MembershipLevel NIVEAU_INSCRIPTION = MembershipLevel.ROUGE;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new EmailAlreadyExistsException(request.phone());
        }

        // Phase 1 ter — seule demande de rôle acceptée du public : JOURNALISTE
        // (accréditation presse). Tout autre rôle privilégié reste une décision
        // admin exclusive ; un client ne choisit jamais ENTRAINEUR/PRESIDENT.
        boolean demandeAccreditation =
                "JOURNALISTE".equalsIgnoreCase(request.demandeRole());

        User user = User.builder()
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .membershipLevel(NIVEAU_INSCRIPTION)
                .role(demandeAccreditation ? Role.JOURNALISTE : Role.ADHERENT)
                // Un journaliste en attente d'accréditation passe par la file admin.
                .statutCompte(demandeAccreditation ? StatutCompte.EN_ATTENTE : StatutCompte.VALIDE)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .referralCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .referredBy(request.referralCode())
                .active(true)
                .build();

        userRepository.save(user);

        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getEmail());

        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode(),
                user.getRole().name()
        );
    }

    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // Tolérance de saisie : trim + casse insensible (les emails ne sont pas
        // sensibles à la casse) ; le mot de passe, lui, reste strict.
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // S1 : un compte désactivé par l'admin ne doit plus pouvoir obtenir
        // de tokens. Même message que mauvaises credentials (pas d'énumération).
        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        // Phase 0 : un compte en attente de validation ou refusé ne peut pas
        // se connecter. Message explicite (le compte existe, ce n'est pas un
        // problème d'identifiants) mais sans révéler si l'email existe pour
        // un autre cas — ici les credentials SONT valides.
        if (user.getStatutCompte() != StatutCompte.VALIDE) {
            throw new CompteNonValideException(user.getStatutCompte(), user.getMotifRefus());
        }

        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getEmail());

        createSession(user.getEmail(), accessToken, ipAddress, userAgent);

        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode(),
                user.getRole().name()
        );
    }

    public MemberCardResponse getMemberCard(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        String qrData = String.format("WAC-%s|%s|%s|%s",
                user.getMembershipLevel(),
                user.getEmail(),
                user.getReferralCode(),
                user.getId());

        String qrCodeBase64;
        try {
            qrCodeBase64 = qrCodeService.generateQrCode(qrData, 300, 300);
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération QR Code", e);
        }

        return new MemberCardResponse(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode(),
                qrCodeBase64
        );
    }

    public byte[] generateAttestation(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        try {
            return pdfService.generateAttestation(user);
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF", e);
        }
    }

    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String userAgent) {
        // S2 : exiger typ=refresh — un access token (volé) ne doit jamais
        // permettre de régénérer un couple de tokens.
        if (!jwtUtils.validateRefreshToken(request.refreshToken())) {
            throw new RuntimeException("Refresh token invalide");
        }
        // S4 : le refresh est rattaché aux sessions révocables — si toutes les
        // sessions du compte ont été révoquées ("déconnexion partout"), plus
        // aucun refresh n'est accepté.
        if (!activeSessionRepository.existsByEmailAndRevokedFalse(jwtUtils.getEmailFromToken(request.refreshToken()))) {
            throw new RuntimeException("Session révoquée : reconnectez-vous");
        }
        String email = jwtUtils.getEmailFromToken(request.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        // S1 bis : pareil qu'au login — compte désactivé ou non validé
        // (Phase 0) : aucun refresh n'est délivré.
        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }
        if (user.getStatutCompte() != StatutCompte.VALIDE) {
            throw new CompteNonValideException(user.getStatutCompte(), user.getMotifRefus());
        }

        String accessToken = jwtUtils.generateAccessToken(user.getId(), email, user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), email);

        createSession(email, accessToken, ipAddress, userAgent);

        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode(),
                user.getRole().name()
        );
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    public AuthResponse upgradeLevel(UpgradeRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        if (request.newLevel().getPrice() <= user.getMembershipLevel().getPrice()) {
            throw new RuntimeException("Le nouveau niveau doit être supérieur au niveau actuel");
        }

        user.setMembershipLevel(request.newLevel());
        user.setMembershipExpiresAt(LocalDateTime.now().plusYears(1));
        userRepository.save(user);

        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getEmail());

        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode(),
                user.getRole().name()
        );
    }

    public MembershipStatusResponse checkMembershipStatus(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = user.getMembershipExpiresAt();
        long daysRemaining = ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate());

        String status;
        String message;

        if (daysRemaining < 0) {
            status = "EXPIRE";
            message = "Votre adhésion a expiré. Renouvelez pour continuer.";
        } else if (daysRemaining == 0) {
            status = "J-1";
            message = "Votre adhésion expire aujourd'hui !";
        } else if (daysRemaining <= 7) {
            status = "J-7";
            message = "Votre adhésion expire dans " + daysRemaining + " jours.";
        } else if (daysRemaining <= 30) {
            status = "J-30";
            message = "Votre adhésion expire dans " + daysRemaining + " jours. Pensez à renouveler.";
        } else {
            status = "ACTIF";
            message = "Adhésion active. Expire le " + expiresAt.toLocalDate() + ".";
        }

        return new MembershipStatusResponse(
                user.getEmail(),
                user.getMembershipLevel(),
                expiresAt,
                status,
                message,
                (int) daysRemaining
        );
    }

    public void sendOtp(OtpRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));
        otpService.generateOtp(user.getEmail());
    }

    /** Canal de démonstration : null si app.otp.mock-delivery=false. */
    public String getMockOtpCode(String email) {
        return otpService.peekMockCode(email);
    }

    public boolean verifyOtp(OtpVerifyRequest request) {
        return otpService.verifyOtp(request.email(), request.code());
    }

    /**
     * S6 : réinitialisation de mot de passe via OTP — donne un usage réel au
     * flux OTP (send → verify → reset). Le code est re-vérifié puis consommé
     * (verifyOtp le supprime) ; les sessions actives sont révoquées pour
     * couper les éventuels voleurs de session.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!otpService.verifyOtp(request.email(), request.otpCode())) {
            throw new RuntimeException("Code OTP invalide ou expiré");
        }
        if (request.newPassword() == null || request.newPassword().length() < 6) {
            throw new RuntimeException("Le nouveau mot de passe doit contenir au moins 6 caractères");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        activeSessionRepository.deleteByEmail(user.getEmail());
    }

    // ============================================
    // KYC Mock
    // ============================================
    public KycResponse uploadKyc(KycUploadRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        KycDocument doc = KycDocument.builder()
                .email(request.email())
                .documentType(request.documentType())
                .documentNumber(request.documentNumber())
                .filePath(request.filePath())
                .verified(false)
                .build();

        kycDocumentRepository.save(doc);
        return new KycResponse(doc.getEmail(), doc.getDocumentType(), doc.getDocumentNumber(), doc.isVerified(), doc.getUploadedAt());
    }

    /**
     * Phase 1 — upload RÉEL du justificatif : le fichier part sur Cloudinary
     * (folder privé kyc-documents), seuls publicId + URL sécurisée sont
     * stockés. Un seul dossier KYC actif par utilisateur (le nouveau remplace
     * l'ancien, non vérifié ou vérifié).
     */
    public KycResponse uploadKycFile(org.springframework.web.multipart.MultipartFile file,
                                     String email, String documentType, String documentNumber) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        try {
            CloudinaryService.UploadResult up = cloudinaryService.uploadKycDocument(file, email);

            // Remplace tout dossier KYC précédent du même utilisateur.
            kycDocumentRepository.deleteByEmail(email);

            KycDocument doc = KycDocument.builder()
                    .email(email)
                    .documentType(documentType)
                    .documentNumber(documentNumber)
                    .filePath(up.publicId())
                    .secureUrl(up.secureUrl())
                    .verified(false)
                    .build();
            kycDocumentRepository.save(doc);

            // Le dépôt d'un justificatif complet marque la demande comme
            // soumise : elle repart dans le circuit de validation admin.
            if (user.getStatutCompte() == StatutCompte.REFUSE) {
                user.setStatutCompte(StatutCompte.EN_ATTENTE);
                user.setMotifRefus(null);
                userRepository.save(user);
            }

            return new KycResponse(doc.getEmail(), doc.getDocumentType(), doc.getDocumentNumber(), doc.isVerified(), doc.getUploadedAt());
        } catch (java.io.IOException e) {
            // Phase 1 ter : panne Cloudinary → message actionnable pour le
            // membre, détail technique réservé aux logs serveur.
            throw new com.wydad.digital.auth.exception.CloudinaryIndisponibleException(e.getMessage());
        }
    }

    /** Vue admin d'un justificatif : métadonnées + URL signée temporaire. */
    public record KycDocumentView(String email, String documentType, String documentNumber,
                                  boolean verified, LocalDateTime uploadedAt, String documentUrl) {}

    /**
     * Phase 1 bis — consultation du justificatif par l'ADMIN : renvoie les
     * métadonnées du dossier KYC et une URL signée Cloudinary (1 h) pour
     * examiner la pièce avant de valider ou refuser le compte.
     */
    public KycDocumentView getKycDocumentView(String email) {
        KycDocument doc = kycDocumentRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Aucun justificatif déposé pour " + email));
        return new KycDocumentView(doc.getEmail(), doc.getDocumentType(),
                doc.getDocumentNumber(), doc.isVerified(), doc.getUploadedAt(),
                cloudinaryService.signedUrl(doc.getFilePath(), doc.getSecureUrl()));
    }

    public KycResponse verifyKyc(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        KycDocument doc = kycDocumentRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Document KYC non trouvé pour cet utilisateur"));

        doc.setVerified(true);
        user.setKycVerified(true);
        kycDocumentRepository.save(doc);
        userRepository.save(user);

        return new KycResponse(doc.getEmail(), doc.getDocumentType(), doc.getDocumentNumber(), doc.isVerified(), doc.getUploadedAt());
    }

    // ============================================
    // Sessions Actives
    // ============================================
    private void createSession(String email, String token, String ipAddress, String userAgent) {
        ActiveSession session = ActiveSession.builder()
                .email(email)
                .token(token)
                .ipAddress(ipAddress != null ? ipAddress : "unknown")
                .userAgent(userAgent != null ? userAgent : "unknown")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .revoked(false)
                .build();
        activeSessionRepository.save(session);
    }

    public List<SessionResponse> getActiveSessions(String email, String currentToken) {
        List<ActiveSession> sessions = activeSessionRepository.findByEmailAndRevokedFalse(email);
        return sessions.stream()
                .map(s -> new SessionResponse(
                        s.getId(),
                        s.getIpAddress(),
                        s.getUserAgent(),
                        s.getCreatedAt(),
                        s.getExpiresAt(),
                        s.getToken().equals(currentToken)
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeSession(Long sessionId, String email) {
        ActiveSession session = activeSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session non trouvée"));

        if (!session.getEmail().equals(email)) {
            throw new RuntimeException("Vous ne pouvez pas révoquer cette session");
        }

        session.setRevoked(true);
        activeSessionRepository.save(session);
    }

    @Transactional
    public void revokeAllSessions(String email, String currentToken) {
        List<ActiveSession> sessions = activeSessionRepository.findByEmailAndRevokedFalse(email);
        for (ActiveSession session : sessions) {
            if (!session.getToken().equals(currentToken)) {
                session.setRevoked(true);
                activeSessionRepository.save(session);
            }
        }
    }

    // ============================================
    // UPDATE PROFILE + DELETE ACCOUNT
    // ============================================
    public AuthResponse updateProfile(UpdateProfileRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.ville() != null) user.setVille(request.ville());
        if (request.langue() != null) user.setLangue(request.langue());
        if (request.timezone() != null) user.setTimezone(request.timezone());
        if (request.bio() != null) user.setBio(request.bio());

        userRepository.save(user);

        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getEmail());

        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode(),
                user.getRole().name()
        );
    }

    @Transactional
    public void deleteAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        activeSessionRepository.deleteByEmail(email);

        kycDocumentRepository.findByEmail(email).ifPresent(doc ->
                kycDocumentRepository.deleteById(doc.getId())
        );

        userRepository.delete(user);
    }

    // ============================================
    // ADMIN ENDPOINTS
    // ============================================
    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToProfile)
                .collect(Collectors.toList());
    }

    /** Destinataires actifs pour le broadcast du notification-service (interne). */
    public List<UserProfileResponse> getAllActiveUsers() {
        return userRepository.findByActiveTrue().stream()
                .map(this::mapToProfile)
                .collect(Collectors.toList());
    }

    private UserProfileResponse mapToProfile(User user) {
        return new UserProfileResponse(
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
    }

    @Transactional
    public void toggleUserActiveStatus(Long id, boolean status) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(status);
        userRepository.save(user);
    }

    @Transactional
    public void changeUserRole(Long id, String roleName) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        Role newRole = Role.valueOf(roleName.toUpperCase());
        user.setRole(newRole);

        // Phase 0 : l'attribution d'un rôle privilégié repasse TOUJOURS par la
        // validation de l'admin (vérification des justificatifs) — même si le
        // compte était VALIDE en tant qu'adhérent. Un compte déjà REFUSE est
        // ré-examiné.
        if (newRole == Role.ENTRAINEUR || newRole == Role.JOURNALISTE || newRole == Role.PRESIDENT) {
            user.setStatutCompte(StatutCompte.EN_ATTENTE);
            user.setMotifRefus(null);
        }
        userRepository.save(user);
    }

    // ============================================
    // Phase 0 — Circuit de validation des comptes
    // ============================================

    /** Demandes en attente (écran admin « demandes de comptes »). */
    public List<UserProfileResponse> getPendingAccounts() {
        return userRepository.findByStatutCompte(StatutCompte.EN_ATTENTE).stream()
                .map(this::mapToProfile)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserProfileResponse validateAccount(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getStatutCompte() == StatutCompte.VALIDE) {
            throw new RuntimeException("Ce compte est déjà validé");
        }
        if (user.getRole() != Role.ADMIN
                && !(user.getRole() == Role.ENTRAINEUR || user.getRole() == Role.JOURNALISTE || user.getRole() == Role.PRESIDENT)) {
            throw new RuntimeException("Seuls les comptes à rôle privilégié nécessitent une validation");
        }
        user.setStatutCompte(StatutCompte.VALIDE);
        user.setMotifRefus(null);
        userRepository.save(user);
        notifyCompteDecision(user, true, null); // Phase 1 ter : in-app best-effort
        return mapToProfile(user);
    }

    @Transactional
    public UserProfileResponse refuseAccount(Long id, String motif) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getStatutCompte() == StatutCompte.VALIDE) {
            throw new RuntimeException("Impossible de refuser un compte déjà validé");
        }
        if (motif == null || motif.isBlank()) {
            throw new RuntimeException("Un motif de refus est obligatoire");
        }
        user.setStatutCompte(StatutCompte.REFUSE);
        user.setMotifRefus(motif.trim());
        userRepository.save(user);
        // Le refus coupe aussi les éventuelles sessions ouvertes.
        activeSessionRepository.deleteByEmail(user.getEmail());
        notifyCompteDecision(user, false, motif.trim()); // Phase 1 ter : in-app best-effort
        return mapToProfile(user);
    }

    /**
     * Phase 1 ter — notification in-app au membre après décision de l'admin
     * sur son compte. Best-effort : jamais bloquante.
     */
    private void notifyCompteDecision(User user, boolean valide, String motif) {
        String titre = valide ? "Compte validé" : "Demande de compte refusée";
        String message = valide
                ? "Bonne nouvelle : votre compte a été validé par le club. Vous pouvez maintenant utiliser toutes les fonctionnalités de votre espace."
                : "Votre demande de compte n'a pas été retenue. Motif : " + motif;
        String target = "/profil";
        try {
            notificationClient.notifyUser(user.getId(), user.getEmail(), titre, message, target);
        } catch (Exception e) {
            // Ne doit jamais faire échouer la décision admin elle-même.
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .warn("Notification décision compte non envoyée à {}: {}", user.getId(), e.getMessage());
        }
    }

    @Transactional
    public UserProfileResponse adminCreateUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Role role = Role.valueOf(request.role().toUpperCase());
        MembershipLevel level = request.membershipLevel() != null ? request.membershipLevel() : MembershipLevel.ROUGE;

        User user = User.builder()
                .email(request.email())
                .phone(request.phone() != null ? request.phone() : "")
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .membershipLevel(level)
                .role(role)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .referralCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .active(true)
                .build();

        userRepository.save(user);

        return new UserProfileResponse(
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
    }
}