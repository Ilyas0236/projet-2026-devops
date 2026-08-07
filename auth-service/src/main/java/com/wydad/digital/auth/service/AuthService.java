package com.wydad.digital.auth.service;

import com.wydad.digital.auth.dto.*;
import com.wydad.digital.auth.exception.EmailAlreadyExistsException;
import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.KycDocument;
import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.KycDocumentRepository;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;
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

        String accessToken = jwtUtils.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode()
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UserNotFoundException(request.email());
        }

        String accessToken = jwtUtils.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode()
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

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtUtils.validateToken(request.refreshToken())) {
            throw new RuntimeException("Refresh token invalide");
        }
        String email = jwtUtils.getEmailFromToken(request.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        String accessToken = jwtUtils.generateAccessToken(email);
        String refreshToken = jwtUtils.generateRefreshToken(email);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode()
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

        String accessToken = jwtUtils.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode()
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
    // NOUVEAU : KYC Mock
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
}