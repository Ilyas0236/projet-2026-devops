package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.model.ActiveSession;
import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.ActiveSessionRepository;
import com.wydad.digital.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0/1 ter — circuit de validation des comptes par documents (ISTQB :
 * table de décision rôles × route + transitions d'état StatutCompte) :
 *
 *   Rôle \ Action      | PATCH /admin/accounts/{id}/refuse
 *   -------------------|-----------------------------------
 *   ADMIN              | 200, statut REFUSE + motif persisté
 *   ADHERENT           | 403
 *
 * Transitions d'état :
 *   EN_ATTENTE -> REFUSE            | autorisée (motif obligatoire)
 *   motif vide / absent             | 400 (contrôle @NotBlank + service)
 *   VALIDÉ -> refuse                | 400 « déjà validé » (transition interdite)
 *   REFUSE -> sessions actives      | coupées (deleteByEmail)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:refusaltest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AccountRefusalTest {

    private static final String REFUSE_URL = "/api/auth/admin/accounts/%d/refuse";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActiveSessionRepository activeSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long enAttenteId;
    private Long valideId;

    @BeforeAll
    void seedUsers() {
        User enAttente = userRepository.save(User.builder()
                .email("attente@wydad.ma")
                .phone("+212600000021")
                .password(passwordEncoder.encode("MotDePasse1"))
                .firstName("Attente")
                .lastName("Test")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.ADHERENT)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .active(true)
                .build());
        enAttente.setStatutCompte(StatutCompte.EN_ATTENTE);
        enAttenteId = userRepository.save(enAttente).getId();

        User valide = userRepository.save(User.builder()
                .email("valide@wydad.ma")
                .phone("+212600000022")
                .password(passwordEncoder.encode("MotDePasse2"))
                .firstName("Valide")
                .lastName("Test")
                .membershipLevel(MembershipLevel.OR)
                .role(Role.ADHERENT)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .active(true)
                .build());
        valide.setStatutCompte(StatutCompte.VALIDE);
        valideId = userRepository.save(valide).getId();

        // Session ouverte pour l'utilisateur EN_ATTENTE : le refus doit la couper.
        ActiveSession session = ActiveSession.builder()
                .email("attente@wydad.ma")
                .token("session-ouverte-a-couper")
                .ipAddress("10.0.0.8")
                .userAgent("junit")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        session.setRevoked(false);
        activeSessionRepository.save(session);
    }

    @Test
    @DisplayName("[TD] ADMIN refuse un compte EN_ATTENTE avec motif -> 200, REFUSE + sessions coupées")
    void adminRefuseCompteEnAttente() throws Exception {
        mockMvc.perform(patch(String.format(REFUSE_URL, enAttenteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motif": "Document d'identité illisible"}
                                """)
                        .with(user("admin@wydad.ma").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCompte").value("REFUSE"));

        User refused = userRepository.findById(enAttenteId).orElseThrow();
        assertEquals(StatutCompte.REFUSE, refused.getStatutCompte(),
                "Le compte passe à REFUSE");
        assertTrue(refused.getMotifRefus() != null && refused.getMotifRefus().contains("illisible"),
                "Le motif est persisté tel que saisi par l'admin");
        assertTrue(activeSessionRepository.findByEmailAndRevokedFalse("attente@wydad.ma").isEmpty()
                        || activeSessionRepository.findByEmailAndRevokedFalse("attente@wydad.ma").stream()
                                .noneMatch(s -> "session-ouverte-a-couper".equals(s.getToken())),
                "Les sessions ouvertes du compte refusé sont supprimées");
    }

    @Test
    @DisplayName("[TD] ADHERENT tente un refus -> 403")
    void adherentNePeutPasRefuser() throws Exception {
        mockMvc.perform(patch(String.format(REFUSE_URL, enAttenteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Tentative détournée\"}")
                        .with(user("fan@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isForbidden());

        assertEquals(StatutCompte.EN_ATTENTE,
                userRepository.findById(enAttenteId).orElseThrow().getStatutCompte());
    }

    @Test
    @DisplayName("[État] Motif manquant -> 400 (@NotBlank), compte inchangé")
    void motifManquantRefuse() throws Exception {
        mockMvc.perform(patch(String.format(REFUSE_URL, enAttenteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("admin@wydad.ma").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        assertEquals(StatutCompte.EN_ATTENTE,
                userRepository.findById(enAttenteId).orElseThrow().getStatutCompte());
    }

    @Test
    @DisplayName("[État] Compte déjà VALIDÉ -> 400 « Impossible de refuser »")
    void compteDejaValideNonRefusable() throws Exception {
        mockMvc.perform(patch(String.format(REFUSE_URL, valideId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Trop tard\"}")
                        .with(user("admin@wydad.ma").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Impossible de refuser")));

        assertEquals(StatutCompte.VALIDE,
                userRepository.findById(valideId).orElseThrow().getStatutCompte());
    }
}
