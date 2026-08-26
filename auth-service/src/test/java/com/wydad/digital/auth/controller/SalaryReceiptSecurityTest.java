package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.SalaryReceipt;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.SalaryReceiptRepository;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.util.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Espace Président — reçus de salaires/primes (§11-§15) :
 * <ol>
 *   <li>émission réservée au PRÉSIDENT/ADMIN (un STAFF est refusé 403) ;</li>
 *   <li>ownership strict : un bénéficiaire ne télécharge QUE son reçu —
 *       le PDF d'un autre est refusé en 403, jamais servi ;</li>
 *   <li>GET /mine ne retourne que les reçus du compte connecté ;</li>
 *   <li>défense-en-profondeur : sans headers X-User-* (appel direct hors
 *       gateway) → 401, jamais 200.</li>
 * </ol>
 *
 * H2 en mode compatibilité PostgreSQL (même configuration que les autres tests).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recutest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalaryReceiptSecurityTest {

    private static final String BENEFICIAIRE_EMAIL = "joueur.recu@wydad.ma";
    private static final String AUTRE_EMAIL = "autre.agent@wydad.ma";
    private static final String STAFF_EMAIL = "staff.attaquant@wydad.ma";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SalaryReceiptRepository receiptRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private Long beneficiaireId;
    private Long autreId;
    private Long recuBeneficiaireId;

    @BeforeAll
    void seed() {
        User beneficiaire = userRepository.save(User.builder()
                .email(BENEFICIAIRE_EMAIL)
                .phone("+212600000011")
                .password("bcrypt-hash")
                .firstName("Joueur")
                .lastName("Recu")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.JOUEUR)
                .active(true)
                .build());
        this.beneficiaireId = beneficiaire.getId();

        User autre = userRepository.save(User.builder()
                .email(AUTRE_EMAIL)
                .phone("+212600000012")
                .password("bcrypt-hash")
                .firstName("Autre")
                .lastName("Agent")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.STAFF)
                .active(true)
                .build());
        this.autreId = autre.getId();

        userRepository.save(User.builder()
                .email(STAFF_EMAIL)
                .phone("+212600000013")
                .password("bcrypt-hash")
                .firstName("Staff")
                .lastName("Attaquant")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.STAFF)
                .active(true)
                .build());

        User president = userRepository.save(User.builder()
                .email("president@wydad.ma")
                .phone("+212600000010")
                .password("bcrypt-hash")
                .firstName("President")
                .lastName("WAC")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.PRESIDENT)
                .active(true)
                .build());

        // Un reçu émis par le président pour le JOUEUR.
        SalaryReceipt recu = receiptRepository.save(SalaryReceipt.builder()
                .userId(beneficiaire.getId())
                .userFullName(beneficiaire.getFirstName() + " " + beneficiaire.getLastName())
                .userEmail(beneficiaire.getEmail())
                .receiptType("SALAIRE")
                .amount(new BigDecimal("15000.00"))
                .currency("MAD")
                .periode("Août 2026")
                .reference("WAC-REC-TEST-000001")
                .paymentDate(LocalDate.now())
                .issuedByUserId(president.getId())
                .issuedByName("President WAC")
                .build());
        this.recuBeneficiaireId = recu.getId();
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /** Seul le PRÉSIDENT/ADMIN émet : un STAFF est refusé en 403. */
    @Test
    void emissionReserveeAuPresidentEtAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/salary-receipts")
                        .header("X-User-Email", STAFF_EMAIL)
                        .header("X-User-Role", "STAFF")
                        .contentType("application/json")
                        .content("{\"userId\": " + beneficiaireId
                                + ", \"receiptType\": \"PRIME\", \"amount\": 500}")
                        .with(user(STAFF_EMAIL).roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    /** Sans headers gateway (appel direct au port du service) → 401. */
    @Test
    void appelDirectSansHeadersGatewayEstRejete() throws Exception {
        mockMvc.perform(get("/api/auth/salary-receipts/" + recuBeneficiaireId + "/pdf"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * §24 — le bénéficiaire d'un AUTRE reçu ne doit JAMAIS obtenir le PDF :
     * ownership vérifié côté serveur → 403.
     */
    @Test
    void unJoueurNePeutPasTelechargerLeRecuDUnAutre() throws Exception {
        SalaryReceipt recuAutre = receiptRepository.save(SalaryReceipt.builder()
                .userId(autreId)
                .userFullName("Autre Agent")
                .userEmail(AUTRE_EMAIL)
                .receiptType("PRIME")
                .amount(new BigDecimal("2000.00"))
                .currency("MAD")
                .reference("WAC-REC-TEST-000002")
                .paymentDate(LocalDate.now())
                .issuedByUserId(999L)
                .issuedByName("Présidence")
                .build());

        mockMvc.perform(get("/api/auth/salary-receipts/" + recuAutre.getId() + "/pdf")
                        .header("X-User-Email", BENEFICIAIRE_EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .with(user(BENEFICIAIRE_EMAIL).roles("JOUEUR")))
                .andExpect(status().isForbidden());
    }

    /** Le bénéficiaire télécharge SON reçu : 200 + application/pdf signé OpenPDF. */
    @Test
    void beneficiaireTelechargeSonPropreRecuPdfValide() throws Exception {
        byte[] pdf = mockMvc.perform(get("/api/auth/salary-receipts/"
                        + recuBeneficiaireId + "/pdf")
                        .header("X-User-Email", BENEFICIAIRE_EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .with(user(BENEFICIAIRE_EMAIL).roles("JOUEUR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertTrue(pdf.length > 4 && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F',
                "La réponse doit être un PDF valide (%PDF-)");
    }

    /** GET /mine ne retourne que les reçus DU compte connecté. */
    @Test
    void mineNeRetourneQueSesPropresRecus() throws Exception {
        String body = mockMvc.perform(get("/api/auth/salary-receipts/mine")
                        .header("X-User-Email", BENEFICIAIRE_EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .with(user(BENEFICIAIRE_EMAIL).roles("JOUEUR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(String.valueOf(beneficiaireId)),
                "Les reçus retournés doivent appartenir au bénéficiaire connecté");
        assertFalse(body.contains("\"userId\":" + autreId),
                "Aucun reçu d'un autre agent ne doit apparaître");
    }

    /** Le président émet réellement un reçu via l'API : 201 + référence. */
    @Test
    void presidentEmetUnRecuViaApi() throws Exception {
        mockMvc.perform(post("/api/auth/salary-receipts")
                        .header("X-User-Email", "president@wydad.ma")
                        .header("X-User-Role", "PRESIDENT")
                        .contentType("application/json")
                        .content("{\"userId\": " + autreId
                                + ", \"receiptType\": \"PRIME\", \"amount\": 3000,"
                                + " \"motif\": \"Prime de championnat\"}")
                        .with(user("president@wydad.ma").roles("PRESIDENT")))
                .andExpect(status().isCreated());
    }

    /** Garde-fou JWT : le token signé porte bien le rôle attendu (sanity). */
    @Test
    void jwtPorteBienLeRoleDuCompte() {
        String token = jwtUtils.generateAccessToken(
                beneficiaireId, BENEFICIAIRE_EMAIL, Role.JOUEUR.name());
        assertEquals(Role.JOUEUR.name(), jwtUtils.getRoleFromToken(token));
    }
}
