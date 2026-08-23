package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.User;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.1 — Refus d'elevation de privilege (auth-service) :
 * 1. un ADHERENT qui appelle PATCH /api/auth/admin/users/{id}/role (endpoint
 *    de promotion) est rejete en 403, meme authentifie ;
 * 2. un ADHERENT ne peut pas modifier le profil d'un autre compte via
 *    PUT /api/auth/me : l'email derive du JWT fait foi (anti IDOR).
 *
 * H2 en mode compatibilite PostgreSQL ; a revalider aussi contre le
 * PostgreSQL reel (docker-compose).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private Long victimeId;
    private String adherentToken;

    @BeforeAll
    void seedUsers() {
        User victime = userRepository.save(User.builder()
                .email("victime@wydad.ma")
                .phone("+212600000001")
                .password("bcrypt-hash")
                .firstName("Victime")
                .lastName("Test")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.ADHERENT)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .active(true)
                .build());
        this.victimeId = victime.getId();

        User attaquant = userRepository.save(User.builder()
                .email("attaquant@wydad.ma")
                .phone("+212600000002")
                .password("bcrypt-hash")
                .firstName("Attaquant")
                .lastName("Test")
                .membershipLevel(MembershipLevel.ROUGE)
                .role(Role.ADHERENT)
                .membershipExpiresAt(LocalDateTime.now().plusYears(1))
                .active(true)
                .build());

        // Vrai access token signe par le service : role ADHERENT dans les claims.
        this.adherentToken = jwtUtils.generateAccessToken(
                attaquant.getId(), attaquant.getEmail(), attaquant.getRole().name());
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /**
     * Un simple adhérent, pourtant authentifie avec un JWT valide, doit se
     * voir refuser l'appel au endpoint admin de changement de role : la
     * regle @PreAuthorize("hasRole('ADMIN')") est prouvee cote serveur.
     */
    @Test
    void unAdherentNePeutPasSePromouvoirAdminViaLApi() throws Exception {
        mockMvc.perform(patch("/api/auth/admin/users/" + victimeId + "/role")
                        .param("newRole", "ADMIN")
                        .header("Authorization", "Bearer " + adherentToken)
                        .with(user("attaquant@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isForbidden());

        assertEquals(Role.ADHERENT,
                userRepository.findById(victimeId).orElseThrow().getRole(),
                "Le compte cible ne doit PAS avoir change de role");
    }

    /** Meme sans header JWT du tout : 403, jamais 200. */
    @Test
    void changementDeRoleSansAuthentificationEstRejete() throws Exception {
        // Contexte PER_CLASS partage : on remet le compte cible en ADHERENT
        // pour que ce test ne depende pas de l'ordre d'execution.
        User victime = userRepository.findById(victimeId).orElseThrow();
        victime.setRole(Role.ADHERENT);
        userRepository.save(victime);

        mockMvc.perform(patch("/api/auth/admin/users/" + victimeId + "/role")
                        .param("newRole", "ADMIN")
                        .with(user("anonyme@mail.ma")))
                .andExpect(status().isForbidden());

        assertEquals(Role.ADHERENT,
                userRepository.findById(victimeId).orElseThrow().getRole(),
                "Le compte cible reste ADHERENT");
    }

    /**
     * PUT /api/auth/me : un adhérent qui met l'email d'un AUTRE compte dans
     * son body ne modifie pas ce compte — l'email du JWT ecrase celui du body.
     */
    @Test
    void updateProfileNePeutPasToucherLeCompteDUnAutre() throws Exception {
        String bodyAvant = userRepository.findById(victimeId).orElseThrow().getFirstName();

        String body = """
                {
                  "email": "victime@wydad.ma",
                  "firstName": "PIRATE",
                  "lastName": "HACKED"
                }
                """;

        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", "Bearer " + adherentToken)
                        .contentType("application/json")
                        .content(body)
                        .with(user("attaquant@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isOk()); // la requete aboutit... sur SON propre profil

        User victime = userRepository.findById(victimeId).orElseThrow();
        assertEquals(bodyAvant, victime.getFirstName(),
                "Le profil de la victime n'a PAS ete modifie");
        // L'email du JWT (attaquant) ecrase celui du body : c'est SON profil
        // qui recoit la modification — exactement le comportement anti-IDOR.
        assertEquals("PIRATE",
                userRepository.findByEmail("attaquant@wydad.ma").orElseThrow().getFirstName(),
                "La modification a ete appliquee au compte de l'appelant, pas a la victime");
    }

    /** L'admin, lui, peut changer un role : la regle n'interdit que l'escalade. */
    @Test
    void seulLAdminPeutChangerUnRole() throws Exception {
        mockMvc.perform(patch("/api/auth/admin/users/" + victimeId + "/role")
                        .param("newRole", "STAFF")
                        .with(user("admin@wac.ma").roles("ADMIN")))
                .andExpect(status().isOk());

        assertEquals(Role.STAFF,
                userRepository.findById(victimeId).orElseThrow().getRole(),
                "Le changement de role par l'ADMIN fonctionne");
    }
}
