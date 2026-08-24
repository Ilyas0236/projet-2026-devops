package com.wydad.digital.auth.service;

import com.wydad.digital.auth.client.NotificationClient;
import com.wydad.digital.auth.dto.RegisterRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 1 ter (Phase F) — demande d'accréditation presse à l'inscription.
 * Partitions ISTQB :
 *  - demandeRole = JOURNALISTE → compte JOURNALISTE en EN_ATTENTE (file admin) ;
 *  - demandeRole = ENTRAINEUR / PRESIDENT (rôles non sollicitables) → ignoré,
 *    compte ADHERENT VALIDE classique ;
 *  - demandeRole absent/null → inscription ADHERENT inchangée.
 */
@ExtendWith(MockitoExtension.class)
class AuthAccreditationTest {

    @Mock UserRepository userRepository;
    @Mock KycDocumentRepository kycDocumentRepository;
    @Mock ActiveSessionRepository activeSessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock NotificationClient notificationClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, kycDocumentRepository,
                activeSessionRepository, passwordEncoder, jwtUtils,
                null, null, null, null, notificationClient);
    }

    private RegisterRequest demande(String role) {
        return new RegisterRequest("presse@test.ma", "0612345678", "secret123",
                "Nadia", "Berrada", null, role);
    }

    @Test
    @DisplayName("demandeRole=JOURNALISTE crée un compte JOURNALISTE en EN_ATTENTE")
    void journaliste_enAttente() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtUtils.generateAccessToken(any(), any(), any())).thenReturn("tok");
        when(jwtUtils.generateRefreshToken(org.mockito.ArgumentMatchers.nullable(Long.class), any(String.class))).thenReturn("ref");

        authService.register(demande("journaliste")); // casse insensible

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(Role.JOURNALISTE, saved.getRole());
        assertEquals(StatutCompte.EN_ATTENTE, saved.getStatutCompte());
        assertNull(saved.getMotifRefus());
    }

    @Test
    @DisplayName("demandeRole=ENTRAINEUR est ignoré : compte ADHERENT VALIDE")
    void entraineurDemande_ignoree() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtUtils.generateAccessToken(any(), any(), any())).thenReturn("tok");
        when(jwtUtils.generateRefreshToken(org.mockito.ArgumentMatchers.nullable(Long.class), any(String.class))).thenReturn("ref");

        authService.register(demande("ENTRAINEUR"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Role.ADHERENT, captor.getValue().getRole());
        assertEquals(StatutCompte.VALIDE, captor.getValue().getStatutCompte());
    }

    @Test
    @DisplayName("demandeRole=PRESIDENT est ignoré : jamais auto-attribué")
    void presidentDemande_ignoree() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtUtils.generateAccessToken(any(), any(), any())).thenReturn("tok");
        when(jwtUtils.generateRefreshToken(org.mockito.ArgumentMatchers.nullable(Long.class), any(String.class))).thenReturn("ref");

        authService.register(demande("PRESIDENT"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Role.ADHERENT, captor.getValue().getRole());
        assertEquals(StatutCompte.VALIDE, captor.getValue().getStatutCompte());
    }

    @Test
    @DisplayName("sans demandeRole : flux ADHERENT historique inchangé")
    void sansDemande_fluxHistorique() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtUtils.generateAccessToken(any(), any(), any())).thenReturn("tok");
        when(jwtUtils.generateRefreshToken(org.mockito.ArgumentMatchers.nullable(Long.class), any(String.class))).thenReturn("ref");

        authService.register(demande(null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(Role.ADHERENT, saved.getRole());
        assertEquals(StatutCompte.VALIDE, saved.getStatutCompte());
        assertEquals(MembershipLevel.ROUGE, saved.getMembershipLevel());
    }
}
