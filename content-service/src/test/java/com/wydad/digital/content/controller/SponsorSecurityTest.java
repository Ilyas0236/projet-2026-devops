package com.wydad.digital.content.controller;

import com.wydad.digital.content.repository.SponsorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.7 — Preuves serveur des règles d'accès aux sponsors :
 * - lecture publique SANS aucune identite -> 200 (page vitrine) ;
 * - ecriture sans en-tetes d'identite -> 401 ;
 * - ecriture avec un role non-ADMIN (JOUEUR) -> 403 ;
 * - ecriture avec le role ADMIN -> 201 et persistance reelle ;
 * - validation : nom / logo / tier obligatoires -> 400.
 *
 * Identite injectee via X-User-Email + X-User-Role (chaine reelle du
 * UserContextFilter ; les deux en-tetes sont requis pour creer une Authentication).
 */
@SpringBootTest(properties = {
        // H2 en memoire pour les tests ; PostgreSQL revalide au deploiement
        "spring.datasource.url=jdbc:h2:mem:content_sponsors;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class SponsorSecurityTest {

    private static final String BASE = "/api/content/sponsors";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SponsorRepository sponsorRepository;

    @BeforeEach
    void cleanBase() {
        sponsorRepository.deleteAll();
    }

    private static String body(String name, String logoUrl, String tier) {
        return """
                {"name": "%s", "logoUrl": "%s", "websiteUrl": "https://exemple.ma", \
                "tier": "%s", "displayOrder": 1}"""
                .formatted(name, logoUrl, tier);
    }

    @Test
    void lecturePubliqueSansIdentiteRenvoie200() throws Exception {
        mockMvc.perform(get(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void creationSansEnTetesRefuseeEtRienEnBase() throws Exception {
        // Pas d'Authentication -> acces refuse par la chaine Spring Security
        // (403 : aucun AuthenticationEntryPoint personnalise dans ce service).
        mockMvc.perform(post(BASE)
                        .contentType("application/json")
                        .content(body("Sponsor Fantome", "https://logo/fantome.png", "MAIN_SPONSOR")))
                .andExpect(status().isForbidden());
        assertThat(sponsorRepository.count()).isZero();
    }

    @Test
    void creationAvecRoleJoueurRefusee403() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Email", "joueur@wydad.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType("application/json")
                        .content(body("Sponsor Joueur", "https://logo/joueur.png", "SUPPLIER")))
                .andExpect(status().isForbidden());
        assertThat(sponsorRepository.count()).isZero();
    }

    @Test
    void adminPeutCreerUnSponsorPersiste() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(body("Sponsor Officiel", "https://logo/officiel.png", "MAIN_SPONSOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sponsor Officiel"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(sponsorRepository.count()).isEqualTo(1);
        // Le sponsor cree est visible publiquement
        mockMvc.perform(get(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sponsor Officiel"));
    }

    @Test
    void creationSansNomOuLogoOuTierRenvoie400() throws Exception {
        // nom manquant
        mockMvc.perform(post(BASE)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(body("", "https://logo/x.png", "SUPPLIER")))
                .andExpect(status().isBadRequest());

        // logo manquant
        mockMvc.perform(post(BASE)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(body("Sponsor X", "", "SUPPLIER")))
                .andExpect(status().isBadRequest());

        // tier manquant
        mockMvc.perform(post(BASE)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(body("Sponsor X", "https://logo/x.png", "")))
                .andExpect(status().isBadRequest());

        assertThat(sponsorRepository.count()).isZero();
    }
}
