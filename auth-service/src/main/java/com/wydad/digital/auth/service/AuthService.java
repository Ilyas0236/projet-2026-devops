package com.wydad.digital.auth.service;

import com.wydad.digital.auth.dto.*;
import com.wydad.digital.auth.exception.EmailAlreadyExistsException;
import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.ActiveSession;
import com.wydad.digital.auth.model.KycDocument;
import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
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

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new EmailAlreadyExistsException(request.phone());
        }

        User user = User.builder()
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .membershipLevel(request.membershipLevel())
                .role(Role.ADHERENT)
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
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UserNotFoundException(request.email());
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
        if (!jwtUtils.validateToken(request.refreshToken())) {
            throw new RuntimeException("Refresh token invalide");
        }
        String email = jwtUtils.getEmailFromToken(request.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

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

    public String sendOtp(OtpRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));
        String code = otpService.generateOtp(user.getEmail());
        return code;
    }

    public boolean verifyOtp(OtpVerifyRequest request) {
        return otpService.verifyOtp(request.email(), request.code());
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
        user.setRole(Role.valueOf(roleName.toUpperCase()));
        userRepository.save(user);
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
                user.getMembershipExpiresAt(),
                user.getReferralCode(),
                user.isActive(),
                user.isKycVerified(),
                user.getCreatedAt()
        );
    }
}