package com.wydad.digital.auth.service;

import com.wydad.digital.auth.dto.AuthResponse;
import com.wydad.digital.auth.dto.LoginRequest;
import com.wydad.digital.auth.dto.MemberCardResponse;
import com.wydad.digital.auth.dto.RefreshTokenRequest;
import com.wydad.digital.auth.dto.RegisterRequest;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
            throw new RuntimeException("Email deja utilise");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new RuntimeException("Telephone deja utilise");
        }

        User user = User.builder()
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .membershipLevel(request.membershipLevel())
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
                .orElseThrow(() -> new RuntimeException("Email ou mot de passe incorrect"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new RuntimeException("Email ou mot de passe incorrect");
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
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouve"));

        String qrData = String.format("WAC-%s|%s|%s|%s",
                user.getMembershipLevel(),
                user.getEmail(),
                user.getReferralCode(),
                user.getId());

        String qrCodeBase64;
        try {
            qrCodeBase64 = qrCodeService.generateQrCode(qrData, 300, 300);
        } catch (Exception e) {
            throw new RuntimeException("Erreur generation QR Code", e);
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
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouve"));

        try {
            return pdfService.generateAttestation(user);
        } catch (Exception e) {
            throw new RuntimeException("Erreur generation PDF", e);
        }
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtUtils.validateRefreshToken(request.refreshToken())) {
            throw new RuntimeException("Refresh token invalide");
        }
        String email = jwtUtils.getEmailFromToken(request.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouve"));

        String newAccessToken = jwtUtils.generateAccessToken(user.getEmail());
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMembershipLevel(),
                user.getReferralCode()
        );
    }
}