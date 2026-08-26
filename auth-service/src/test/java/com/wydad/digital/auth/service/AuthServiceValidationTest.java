package com.wydad.digital.auth.service;

import com.wydad.digital.auth.dto.UserProfileResponse;
import com.wydad.digital.auth.exception.CompteNonValideException;
import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.ActiveSessionRepository;
import com.wydad.digital.auth.repository.KycDocumentRepository;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 0 — circuit de validation des comptes (statutCompte).
 * Couverture : blocage login/refresh si compte non VALIDE, message explicite,
 * validation/refus admin avec règles métier.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceValidationTest {

    @Mock UserRepository userRepository;
    @Mock KycDocumentRepository kycDocumentRepository;
    @Mock ActiveSessionRepository activeSessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock com.wydad.digital.auth.client.NotificationClient notificationClient;

    private AuthService authService;

    private User user(Role role, StatutCompte statut) {
        return User.builder()
                .email("user@test.ma")
                .phone("0600000000")
                .password("hashed")
                .firstName("Test")
                .lastName("User")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(role)
                .statutCompte(statut)
                .active(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, kycDocumentRepository,
                activeSessionRepository, passwordEncoder, jwtUtils, null, null,
                null, null, notificationClient, null, null);
    }

    // ---------- Login bloqué si compte non VALIDE ----------

    @Test
    @DisplayName("login refusé avec CompteNonValideException si EN_ATTENTE")
    void login_enAttente_blocked() {
        User u = user(Role.ENTRAINEUR, StatutCompte.EN_ATTENTE);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThrows(CompteNonValideException.class,
                () -> authService.login(new com.wydad.digital.auth.dto.LoginRequest(
                        "user@test.ma", "secret123"), "1.2.3.4", "junit"));
    }

    @Test
    @DisplayName("login REFUSE : l'exception porte le motif de refus")
    void login_refuse_messageContainsMotif() {
        User u = user(Role.JOURNALISTE, StatutCompte.REFUSE);
        u.setMotifRefus("Documents illisibles");
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(u));
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        CompteNonValideException ex = assertThrows(CompteNonValideException.class,
                () -> authService.login(new com.wydad.digital.auth.dto.LoginRequest(
                        "user@test.ma", "secret123"), "1.2.3.4", "junit"));
        assertTrue(ex.getMessage().contains("Documents illisibles"));
        assertEquals(StatutCompte.REFUSE, ex.getStatut());
    }

    @Test
    @DisplayName("login accepté si compte VALIDE (rôle privilégié validé)")
    void login_valide_ok() {
        User u = user(Role.PRESIDENT, StatutCompte.VALIDE);
        u.setId(1L);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtils.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn("access");
        lenient().when(jwtUtils.generateRefreshToken(anyLong(), anyString())).thenReturn("refresh");

        var response = authService.login(new com.wydad.digital.auth.dto.LoginRequest(
                "user@test.ma", "secret123"), "1.2.3.4", "junit");
        assertEquals("access", response.accessToken());
    }

    @Test
    @DisplayName("refresh refusé si statut non VALIDE")
    void refresh_enAttente_blocked() {
        User u = user(Role.ENTRAINEUR, StatutCompte.EN_ATTENTE);
        String refreshToken = "valid-refresh";
        when(jwtUtils.validateRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtils.getEmailFromToken(refreshToken)).thenReturn("user@test.ma");
        when(activeSessionRepository.existsByEmailAndRevokedFalse("user@test.ma")).thenReturn(true);
        when(userRepository.findByEmail("user@test.ma")).thenReturn(Optional.of(u));

        assertThrows(CompteNonValideException.class,
                () -> authService.refreshToken(new com.wydad.digital.auth.dto.RefreshTokenRequest(
                        refreshToken), "1.2.3.4", "junit"));
    }

    // ---------- Circuit admin ----------

    @Test
    @DisplayName("validateAccount passe EN_ATTENTE à VALIDE et efface le motif")
    void validateAccount_pendingToValid() {
        User u = user(Role.JOURNALISTE, StatutCompte.EN_ATTENTE);
        u.setId(7L); // en réel, l'ID vient de la séquence DB
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        UserProfileResponse resp = authService.validateAccount(7L);
        assertEquals(StatutCompte.VALIDE, u.getStatutCompte());
        assertNull(u.getMotifRefus());
        assertEquals(StatutCompte.VALIDE, resp.statutCompte());

        // Phase 1 ter — le membre est prévenu in-app de la validation.
        verify(notificationClient).notifyUser(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("user@test.ma"),
                org.mockito.ArgumentMatchers.contains("valid"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("validateAccount refuse un compte déjà VALIDE (400)")
    void validateAccount_alreadyValid_throws() {
        User u = user(Role.JOURNALISTE, StatutCompte.VALIDE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThrows(RuntimeException.class, () -> authService.validateAccount(7L));
    }

    @Test
    @DisplayName("refuseAccount exige un motif non vide")
    void refuseAccount_blankMotif_throws() {
        User u = user(Role.ENTRAINEUR, StatutCompte.EN_ATTENTE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThrows(RuntimeException.class, () -> authService.refuseAccount(7L, "  "));
        assertEquals(StatutCompte.EN_ATTENTE, u.getStatutCompte());
    }

    @Test
    @DisplayName("refuseAccount enregistre le motif et révoque les sessions")
    void refuseAccount_savesMotif_andRevokesSessions() {
        User u = user(Role.ENTRAINEUR, StatutCompte.EN_ATTENTE);
        u.setId(7L); // en réel, l'ID vient de la séquence DB
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        authService.refuseAccount(7L, "Pièces justificatives manquantes");
        assertEquals(StatutCompte.REFUSE, u.getStatutCompte());
        assertEquals("Pièces justificatives manquantes", u.getMotifRefus());
        verify(activeSessionRepository).deleteByEmail("user@test.ma");

        // Phase 1 ter — la notification de refus reprend le motif.
        verify(notificationClient).notifyUser(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("user@test.ma"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("Pièces justificatives manquantes"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("changeUserRole vers rôle privilégié remet le compte EN_ATTENTE")
    void changeUserRole_privileged_resetsPending() {
        User u = user(Role.ADHERENT, StatutCompte.VALIDE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        authService.changeUserRole(7L, "PRESIDENT");
        assertEquals(Role.PRESIDENT, u.getRole());
        assertEquals(StatutCompte.EN_ATTENTE, u.getStatutCompte());
    }

    @Test
    @DisplayName("changeUserRole vers ADHERENT ne touche pas au statut VALIDE")
    void changeRole_adherent_keepsValid() {
        User u = user(Role.ENTRAINEUR, StatutCompte.VALIDE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        authService.changeUserRole(7L, "ADHERENT");
        assertEquals(Role.ADHERENT, u.getRole());
        assertEquals(StatutCompte.VALIDE, u.getStatutCompte());
    }
}
