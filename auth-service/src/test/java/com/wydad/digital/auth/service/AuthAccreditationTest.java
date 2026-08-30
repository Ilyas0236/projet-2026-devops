package com.wydad.digital.auth.service;

import com.wydad.digital.auth.client.ContentClient;
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
 * Inscription multi-statuts — demande de rôle à l'inscription.
 * Partitions ISTQB :
 *  - demandeRole = JOURNALISTE → EN_ATTENTE + organismePresse obligatoire ;
 *  - demandeRole = JOUEUR / ENTRAINEUR / STAFF → EN_ATTENTE + catégorie
 *    obligatoire parmi U15/U17/U18/U20/SENIOR (casse insensible) ;
 *  - catégorie manquante / invalide → IllegalArgumentException ;
 *  - organisme presse manquant → IllegalArgumentException ;
 *  - rôle non sollicitable (PRESIDENT, ADMIN...) → IllegalArgumentException ;
 *  - demandeRole absent/null → inscription ADHERENT VALIDE inchangée.
 */
@ExtendWith(MockitoExtension.class)
class AuthAccreditationTest {

    @Mock UserRepository userRepository;
    @Mock KycDocumentRepository kycDocumentRepository;
    @Mock ActiveSessionRepository activeSessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock NotificationClient notificationClient;
    @Mock ContentClient contentClient;
    @Mock com.wydad.digital.auth.client.SportsClient sportsClient;
    @Mock com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository userSubscriptionRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, kycDocumentRepository,
                activeSessionRepository, passwordEncoder, jwtUtils,
                null, null, null, null, notificationClient, contentClient, sportsClient,
                userSubscriptionRepository);
    }

    /** Fabrique une demande complète ; les champs conditionnels sont null par défaut. */
    private RegisterRequest demande(String role, String discipline, String categorie,
                                    String organismePresse, Long matchId) {
        return new RegisterRequest("presse@test.ma", "0612345678", "secret123",
                "Nadia", "Berrada", null, role, discipline, categorie, organismePresse, matchId);
    }

    /** Stub des mocks communs à tout register() qui aboutit. Les stubs JWT ne
     * servent qu'au flux ADHERENT (VALIDE) — les comptes EN_ATTENTE n'en
     * reçoivent plus aucun depuis le correctif sécurité 26/08. */
    private void stubRegisterOk() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtUtils.generateAccessToken(any(), any(), any())).thenReturn("tok");
        when(jwtUtils.generateRefreshToken(org.mockito.ArgumentMatchers.nullable(Long.class), any(String.class))).thenReturn("ref");
    }

    /** Stubs minimaux pour un register qui crée un compte EN_ATTENTE :
     * aucun stub JWT — si un token était émis, lenient/strict échouerait
     * et surtout generateAccessToken retournerait null (jamais appelé). */
    private void stubRegisterEnAttente() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- JOURNALISTE ----------

    @Test
    @DisplayName("demandeRole=JOURNALISTE + match réel → compte JOURNALISTE EN_ATTENTE avec libellé figé")
    void journaliste_enAttente() {
        stubRegisterEnAttente();
        when(contentClient.fetchMatchLabel(7L)).thenReturn("Wydad vs Raja — Botola Pro, le 2026-09-10");

        authService.register(demande("journaliste", null, null, "SportsDZ.ma", 7L)); // casse insensible

        User saved = savedUser();
        assertEquals(Role.JOURNALISTE, saved.getRole());
        assertEquals(StatutCompte.EN_ATTENTE, saved.getStatutCompte());
        assertEquals("SportsDZ.ma", saved.getOrganismePresse());
        assertEquals(7L, saved.getMatchId());
        assertEquals("Wydad vs Raja — Botola Pro, le 2026-09-10", saved.getMatchSouhaite());
        assertNull(saved.getMotifRefus());
        assertNull(saved.getCategorieDemandee()); // pas de catégorie pour la presse
        verify(jwtUtils, org.mockito.Mockito.never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("JOURNALISTE sans organe de presse → refusé")
    void journaliste_sansOrganisme_ko() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(demande("JOURNALISTE", null, null, null, 7L)));
        assertTrue(ex.getMessage().contains("organe de presse"));
    }

    @Test
    @DisplayName("§17 : JOURNALISTE sans matchId → refusé (texte libre interdit)")
    void journaliste_sansMatch_ko() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(demande("JOURNALISTE", null, null, "SportsDZ.ma", null)));
        assertTrue(ex.getMessage().contains("match réel"));
    }

    @Test
    @DisplayName("§17 : match inexistant dans le calendrier → demande refusée")
    void journaliste_matchInconnu_ko() {
        when(contentClient.fetchMatchLabel(999L)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(demande("JOURNALISTE", null, null, "SportsDZ.ma", 999L)));
        assertTrue(ex.getMessage().contains("Match introuvable"));
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    // ---------- Rôles sportifs : catégorie obligatoire ----------

    @Test
    @DisplayName("demandeRole=JOUEUR + SENIOR → JOUEUR EN_ATTENTE avec catégorie")
    void joueur_avecCategorie_enAttente() {
        stubRegisterEnAttente();
        authService.register(demande("JOUEUR", "FOOTBALL", "senior", null, null));

        User saved = savedUser();
        assertEquals(Role.JOUEUR, saved.getRole());
        assertEquals(StatutCompte.EN_ATTENTE, saved.getStatutCompte());
        assertEquals("SENIOR", saved.getCategorieDemandee());
        assertEquals("FOOTBALL", saved.getDisciplineDemandee());
        assertNull(saved.getOrganismePresse());
        verify(jwtUtils, org.mockito.Mockito.never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("demandeRole=ENTRAINEUR + U17 → plus ignoré : EN_ATTENTE avec catégorie")
    void entraineurDemande_enAttente() {
        stubRegisterEnAttente();
        authService.register(demande("ENTRAINEUR", "HANDBALL", "U17", null, null));

        User saved = savedUser();
        assertEquals(Role.ENTRAINEUR, saved.getRole());
        assertEquals(StatutCompte.EN_ATTENTE, saved.getStatutCompte());
        assertEquals("U17", saved.getCategorieDemandee());
        verify(jwtUtils, org.mockito.Mockito.never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("demandeRole=STAFF + U15 → STAFF EN_ATTENTE avec catégorie")
    void staffDemande_enAttente() {
        stubRegisterEnAttente();
        authService.register(demande("STAFF", "AUTRE", "U15", null, null));

        User saved = savedUser();
        assertEquals(Role.STAFF, saved.getRole());
        assertEquals("U15", saved.getCategorieDemandee());
        assertEquals(StatutCompte.EN_ATTENTE, saved.getStatutCompte());
    }

    @Test
    @DisplayName("JOUEUR sans catégorie → refusé")
    void joueur_sansCategorie_ko() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(demande("JOUEUR", "FOOTBALL", null, null, null)));
        assertTrue(ex.getMessage().contains("catégorie"));
    }

    @Test
    @DisplayName("ENTRAINEUR avec catégorie invalide (PRO) → refusé")
    void entraineur_categorieInvalide_ko() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(demande("ENTRAINEUR", "FOOTBALL", "PRO", null, null)));
        assertTrue(ex.getMessage().contains("Catégorie invalide"));
    }

    // ---------- Rôle non sollicitable ----------

    @Test
    @DisplayName("demandeRole=PRESIDENT → jamais auto-attribué, rejeté explicitement")
    void presidentDemande_rejete() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(demande("PRESIDENT", null, null, null, null)));
        assertTrue(ex.getMessage().contains("non sollicitable"));
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("demandeRole=ADMIN → rejeté explicitement")
    void adminDemande_rejete() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(demande("ADMIN", null, null, null, null)));
        assertTrue(ex.getMessage().contains("non sollicitable"));
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    // ---------- Flux historique ----------

    @Test
    @DisplayName("sans demandeRole : flux ADHERENT historique inchangé (mais plus de carte auto)")
    void sansDemande_fluxHistorique() {
        stubRegisterOk();
        authService.register(demande(null, null, null, null, null));

        User saved = savedUser();
        assertEquals(Role.ADHERENT, saved.getRole());
        assertEquals(StatutCompte.VALIDE, saved.getStatutCompte());
        // Refonte B.12 : la carte n'est plus attribuée à l'inscription —
        // elle est générée 100% à partir de l'abonnement saisonnier acheté.
        assertNull(saved.getMembershipLevel(),
                "Aucun membershipLevel à l'inscription : la carte vient de l'abonnement");
        assertNull(saved.getCategorieDemandee());
    }
}
