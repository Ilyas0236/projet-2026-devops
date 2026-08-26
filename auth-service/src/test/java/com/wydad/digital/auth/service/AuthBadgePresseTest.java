package com.wydad.digital.auth.service;

import com.wydad.digital.auth.exception.CompteNonValideException;
import com.wydad.digital.auth.exception.UserNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Accréditation presse — badge PDF+QR. Partitions ISTQB :
 *  - JOURNALISTE VALIDÉ → PDF non vide avec signature %PDF ;
 *  - rôle non journaliste → IllegalArgumentException ;
 *  - JOURNALISTE EN_ATTENTE → CompteNonValideException (pas de badge avant
 *    validation admin) ;
 *  - utilisateur inconnu → UserNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class AuthBadgePresseTest {

    @Mock UserRepository userRepository;
    @Mock KycDocumentRepository kycDocumentRepository;
    @Mock ActiveSessionRepository activeSessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock NotificationClientStub notificationClient;
    // QrCodeService réel : la génération ZXing est locale, sans réseau.

    private AuthService authService;

    /** Stub du NotificationClient sans dépendre du vrai type externe. */
    interface NotificationClientStub {}

    private User journaliste(StatutCompte statut) {
        return User.builder()
                .email("presse@test.ma")
                .phone("0612345678")
                .password("hashed")
                .firstName("Nadia")
                .lastName("Berrada")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.JOURNALISTE)
                .statutCompte(statut)
                .organismePresse("SportsDZ.ma")
                .matchSouhaite("WAC vs RCA")
                .build();
    }

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, kycDocumentRepository,
                activeSessionRepository, passwordEncoder, jwtUtils,
                new QrCodeService(), new PdfService(), null, null,
                null, null, null);
        lenient().when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(journaliste(StatutCompte.VALIDE)));
    }

    @Test
    @DisplayName("JOURNALISTE validé → badge PDF généré (signature %PDF)")
    void badge_journalisteValide() {
        byte[] pdf = authService.generateBadgePresse("presse@test.ma");
        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "Le PDF doit contenir le badge + QR");
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    @DisplayName("rôle ADHERENT → refusé : badge réservé à la presse")
    void badge_roleNonJournaliste_ko() {
        User adherent = journaliste(StatutCompte.VALIDE);
        adherent.setRole(Role.ADHERENT);
        when(userRepository.findByEmail("presse@test.ma")).thenReturn(Optional.of(adherent));

        assertThrows(IllegalArgumentException.class,
                () -> authService.generateBadgePresse("presse@test.ma"));
    }

    @Test
    @DisplayName("JOURNALISTE EN_ATTENTE → pas de badge avant validation admin")
    void badge_enAttente_ko() {
        when(userRepository.findByEmail("presse@test.ma"))
                .thenReturn(Optional.of(journaliste(StatutCompte.EN_ATTENTE)));

        assertThrows(CompteNonValideException.class,
                () -> authService.generateBadgePresse("presse@test.ma"));
    }

    @Test
    @DisplayName("utilisateur inconnu → UserNotFoundException")
    void badge_utilisateurInconnu_ko() {
        when(userRepository.findByEmail("inconnu@test.ma")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> authService.generateBadgePresse("inconnu@test.ma"));
    }
}
