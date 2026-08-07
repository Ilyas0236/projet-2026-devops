package com.wydad.digital.auth.service;

import com.wydad.digital.auth.dto.AuthResponse;
import com.wydad.digital.auth.dto.LoginRequest;
import com.wydad.digital.auth.dto.MemberCardResponse;
import com.wydad.digital.auth.dto.RegisterRequest;
import com.wydad.digital.auth.exception.EmailAlreadyExistsException;
import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final QrCodeService qrCodeService;
    private final PdfService pdfService;

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

    public AuthResponse refreshToken(com.wydad.digital.auth.dto.RefreshTokenRequest request) {
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
}