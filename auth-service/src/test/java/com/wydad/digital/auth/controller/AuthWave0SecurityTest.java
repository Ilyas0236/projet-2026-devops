package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.model.ActiveSession;
import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.ActiveSessionRepository;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.service.OtpService;
import com.wydad.digital.auth.util.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vague 0 (audit 2026-08-24) — tests de non-régression des failles corrigées :
 * S1  login/refresh refusent un compte désactivé ;
 * S2  un access token ne peut pas servir de refresh token (typ=refresh exigé) ;
 * S3  l'inscription force le niveau ROUGE, quel que soit le niveau envoyé par le client ;
 * S4  "déconnexion partout" (révocation sessions) invalide les refresh tokens ;
 * S5  member-card/attestation exigent les headers d'identité posés par la gateway ;
 * S6  reset de mot de passe par OTP : bon code → mot de passe changé + sessions coupées,
 *     mauvais code → refus.
 *
 * H2 en mode compatibilité PostgreSQL ; à revalider aussi contre le PostgreSQL
 * réel (docker-compose).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wavetest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthWave0SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActiveSessionRepository activeSessionRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private OtpService otpService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeAll
    void seedUsers() {
        User desactive = userRepository.save(User.builder()
                .email("desactive@wydad.ma")
                .phone("+212600000010")
                .password(passwordEncoder.encode("MotDePasse1"))
                .firstName("Desactive")
                .lastName("Test")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.ADHERENT)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .active(false) // S1 : compte désactivé par l'admin
                .build());

        User membre = userRepository.save(User.builder()
                .email("membre@wydad.ma")
                .phone("+212600000011")
                .password(passwordEncoder.encode("MotDePasse2"))
                .firstName("Membre")
                .lastName("Test")
                .membershipLevel(MembershipLevel.OR)
                .role(Role.ADHERENT)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .active(true)
                .build());

        // Session active pour le membre (S4 : refresh rattaché aux sessions révocables)
        ActiveSession sessionMembre = ActiveSession.builder()
                .email(membre.getEmail())
                .token(jwtUtils.generateRefreshToken(membre.getId(), membre.getEmail()))
                .ipAddress("127.0.0.1")
                .userAgent("junit")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        sessionMembre.setRevoked(false);
        activeSessionRepository.save(sessionMembre);
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ==================== S1 ====================

    /** Un compte désactivé ne peut plus obtenir de tokens au login. */
    @Test
    void s1LoginRefuseUnCompteDesactive() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email": "desactive@wydad.ma", "password": "MotDePasse1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ==================== S2 ====================

    /**
     * Un access token volé ne doit pas pouvoir régénérer un couple de tokens :
     * seul typ=refresh est accepté sur POST /refresh.
     */
    @Test
    void s2UnAccessTokenNePeutPasServirDeRefreshToken() throws Exception {
        User membre = userRepository.findByEmail("membre@wydad.ma").orElseThrow();
        String accessToken = jwtUtils.generateAccessToken(membre.getId(), membre.getEmail(), membre.getRole().name());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\": \"" + accessToken + "\"}"))
                .andExpect(status().is4xxClientError()); // rejeté ("Refresh token invalide"), jamais 200

        assertEquals(0, activeSessionRepository.findByEmailAndRevokedFalse(membre.getEmail()).stream()
                .filter(s -> s.getToken().equals(accessToken))
                .count(),
                "Aucune session n'a dû être créée depuis un access token");
    }

    // ==================== S3 ====================

    /**
     * L'inscription ignore toute notion de niveau/carte — la carte de membre
     * n'est plus attribuée à l'inscription. Elle est générée 100% à partir
     * de l'abonnement saisonnier acheté (cf. refonte B.12). Pour un compte
     * fraîchement créé, {@code membershipLevel} est donc {@code null}.
     */
    @Test
    void s3InscriptionNeForcePlusDeNiveauLeMembreDoitAcheterUneCarte() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email": "pirate@wydad.ma", "phone": "+212600000012",
                                 "password": "MotDePasse3", "firstName": "Pirate", "lastName": "Test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipLevel").doesNotExist())
                .andExpect(jsonPath("$.planName").doesNotExist());

        assertNull(userRepository.findByEmail("pirate@wydad.ma").orElseThrow().getMembershipLevel(),
                "L'inscription n'attribue plus de carte — la carte vient de l'abonnement acheté");
    }

    // ==================== S4 ====================

    /**
     * Après "déconnexion partout" (toutes les sessions révoquées), un refresh
     * token encore valide cryptographiquement doit être refusé.
     */
    @Test
    void s4RefreshApresRevocationDesSessionsEstRefuse() throws Exception {
        User membre = userRepository.findByEmail("membre@wydad.ma").orElseThrow();
        String refreshToken = jwtUtils.generateRefreshToken(membre.getId(), membre.getEmail());

        // Toutes les sessions du membre sont révoquées (comme revoke-all)
        for (ActiveSession s : activeSessionRepository.findByEmailAndRevokedFalse(membre.getEmail())) {
            s.setRevoked(true);
            activeSessionRepository.save(s);
        }

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().is4xxClientError()); // "Session révoquée", jamais 200
    }

    // ==================== S6 ====================

    /**
     * Reset de mot de passe avec un OTP valide : le mot de passe change ET
     * toutes les sessions actives sont supprimées (voleurs de session coupés).
     */
    @Test
    void s6ResetPasswordAvecOtpValideChangeLeMotDePasseEtCoupeLesSessions() throws Exception {
        User cible = userRepository.save(User.builder()
                .email("reset@wydad.ma")
                .phone("+212600000013")
                .password(passwordEncoder.encode("AncienMotDePasse"))
                .firstName("Reset")
                .lastName("Test")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.ADHERENT)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .active(true)
                .build());
        ActiveSession sessionVoleur = ActiveSession.builder()
                .email(cible.getEmail())
                .token("session-a-couper")
                .ipAddress("10.0.0.9")
                .userAgent("navigateur-voleur")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        sessionVoleur.setRevoked(false);
        activeSessionRepository.save(sessionVoleur);

        String code = otpService.generateOtp(cible.getEmail());

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType("application/json")
                        .content("""
                                {"email": "%s", "otpCode": "%s", "newPassword": "NouveauMotDePasse"}
                                """.formatted(cible.getEmail(), code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsString("succès")));

        assertTrue(passwordEncoder.matches("NouveauMotDePasse",
                        userRepository.findByEmail(cible.getEmail()).orElseThrow().getPassword()),
                "Le nouveau mot de passe doit être actif");
        assertFalse(passwordEncoder.matches("AncienMotDePasse",
                userRepository.findByEmail(cible.getEmail()).orElseThrow().getPassword()));
        assertTrue(activeSessionRepository.findByEmailAndRevokedFalse(cible.getEmail()).isEmpty(),
                "Toutes les sessions doivent avoir été supprimées après reset");
    }

    /** Reset avec un mauvais OTP : refus, mot de passe inchangé. */
    @Test
    void s6ResetPasswordAvecMauvaisOtpEstRefuse() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType("application/json")
                        .content("""
                                {"email": "reset@wydad.ma", "otpCode": "000000", "newPassword": "AutreMotDePasse"}
                                """))
                .andExpect(status().isBadRequest());

        assertTrue(passwordEncoder.matches("NouveauMotDePasse",
                        userRepository.findByEmail("reset@wydad.ma").orElseThrow().getPassword()),
                "Le mot de passe ne doit PAS avoir changé sans OTP valide");
    }

    // ==================== S5 ====================

    /**
     * member-card sans headers d'identité X-User-* (appel direct au service,
     * hors gateway) : refus systématique — défense en profondeur.
     */
    @Test
    void s5MemberCardSansHeadersGatewayEstRefusee() throws Exception {
        mockMvc.perform(get("/api/auth/member-card")
                        .param("email", "membre@wydad.ma")
                        .with(user("anonyme@mail.ma")))
                .andExpect(status().isUnauthorized());
    }

    /** member-card authentifiée mais sur le compte d'un AUTRE : 403. */
    @Test
    void s5MemberCardDUnAutreCompteEstInterdite() throws Exception {
        mockMvc.perform(get("/api/auth/member-card")
                        .param("email", "reset@wydad.ma")
                        .header("X-User-Email", "membre@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .with(user("membre@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isForbidden());
    }

    /**
     * Cas nominal : un adhérent SANS abonnement actif reçoit 404 — la carte
     * n'est générée qu'après l'achat (cf. refonte B.12). Le front doit alors
     * proposer un CTA « Acheter mon abonnement ».
     */
    @Test
    void s5MemberCardPropreFonctionneViaHeadersGateway() throws Exception {
        mockMvc.perform(get("/api/auth/member-card")
                        .param("email", "membre@wydad.ma")
                        .header("X-User-Email", "membre@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .with(user("membre@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isNotFound())
                // Le 404 vient du UserNotFoundException levé par getMemberCard
                // (message « Aucun abonnement actif pour cet utilisateur »).
                .andExpect(jsonPath("$.message",
                        containsString("Aucun abonnement actif")));
    }
}
