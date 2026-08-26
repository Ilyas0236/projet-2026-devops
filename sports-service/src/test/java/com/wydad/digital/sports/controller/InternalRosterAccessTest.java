package com.wydad.digital.sports.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-régression du 25/08 (campagne de re-test) : la route roster interne
 * doit être joignable par un appel service-à-service — c'est-à-dire SANS
 * en-têtes X-User-* — et être protégée par le secret partagé validé DANS
 * InternalRosterController.
 *
 * <p>Avant correctif, SecurityConfig exigeait anyRequest().authenticated()
 * sans dérogation pour /api/sports/internal/** : Spring Security rejetait
 * l'appel interne anonyme en 403 AVANT le validateur de secret, et le chat
 * de communication-service tombait en « Roster indisponible » en prod.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rostertest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // Même convention que docker-compose : WYDAD_INTERNAL_SECRET
        "wydad.internal-secret=secret-de-test-interne"
})
@AutoConfigureMockMvc
class InternalRosterAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rosterAvecBonSecretRepondSansIdentiteGateway() throws Exception {
        // Appel interne typique : aucun X-User-* ; doit franchir Spring
        // Security (permitAll) puis être validé par le secret dans le
        // contrôleur. Utilisateur 9 sans fiche -> 404 (et non 403).
        mockMvc.perform(get("/api/sports/internal/roster/membership/9")
                        .header("X-Internal-Secret", "secret-de-test-interne"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rosterAvecMauvaisSecretReste403() throws Exception {
        mockMvc.perform(get("/api/sports/internal/roster/membership/9")
                        .header("X-Internal-Secret", "mauvais-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rosterSansSecretReste403() throws Exception {
        mockMvc.perform(get("/api/sports/internal/roster/membership/9"))
                .andExpect(status().isForbidden());
    }

    @Test
    void membersAvecBonSecretRetourneListe() throws Exception {
        mockMvc.perform(get("/api/sports/internal/roster/members")
                        .param("sportType", "FOOTBALL")
                        .param("category", "SENIOR")
                        .header("X-Internal-Secret", "secret-de-test-interne"))
                .andExpect(status().isOk());
    }
}
