package com.wydad.digital.content.controller;

import com.wydad.digital.content.repository.JoueurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fonctionnalité 6/6 — Stats joueurs affichées publiquement, saisies par
 * l'ADMIN. Preuves serveur du CRUD /api/content/joueurs :
 *  - lecture publique SANS identité -> 200 (page Effectif publique) ;
 *  - écriture sans en-têtes -> rejetée (403), rien persisté ;
 *  - écriture avec role non-ADMIN (JOUEUR) -> 403 ;
 *  - écriture ADMIN -> 201/200 et persistance réelle (les stats saisies
 *    par l'ADMIN sont bien celles servies au public) ;
 *  - validation : nom / poste / age / numero / sport obligatoires -> 400.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content_joueurs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class JoueurSecurityTest {

    private static final String BASE = "/api/content/joueurs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JoueurRepository joueurRepository;

    @BeforeEach
    void cleanBase() {
        joueurRepository.deleteAll();
    }

    private static String validBody() {
        return """
                {"nom": "Ashraf Hakimi", "poste": "Défenseur", "age": 26,
                 "numero": 2, "sport": "FOOTBALL", "matchsJoues": 12,
                 "buts": 3, "passes": 5}""";
    }

    @Test
    @DisplayName("Lecture publique sans aucune identité -> 200 (page Effectif)")
    void lecturePubliqueSansIdentite() throws Exception {
        mockMvc.perform(get(BASE + "/sport/FOOTBALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Création sans en-têtes d'identité -> rejetée, rien persisté")
    void creationSansIdentiteRefusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
        assertThat(joueurRepository.count()).isZero();
    }

    @Test
    @DisplayName("Création par un JOUEUR -> 403")
    void creationParJoueurRefusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
        assertThat(joueurRepository.count()).isZero();
    }

    @Test
    @DisplayName("ADMIN crée puis modifie les stats publiques -> persistance réelle")
    void adminCreeEtModifieLesStats() throws Exception {
        String created = mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.buts").value(3))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        // L'ADMIN met à jour les stats : 13 matchs, 4 buts — le public verra ça.
        mockMvc.perform(put(BASE + "/" + id)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom": "Ashraf Hakimi", "poste": "Défenseur", "age": 26,
                                 "numero": 2, "sport": "FOOTBALL", "matchsJoues": 13,
                                 "buts": 4, "passes": 5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchsJoues").value(13))
                .andExpect(jsonPath("$.buts").value(4));

        // La lecture publique (sans identité) sert bien les valeurs mises à jour.
        mockMvc.perform(get(BASE + "/sport/FOOTBALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchsJoues").value(13))
                .andExpect(jsonPath("$[0].buts").value(4));
        assertThat(joueurRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Suppression réservée ADMIN ; anonyme rejeté")
    void suppressionReserveeAdmin() throws Exception {
        String created = mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        // Anonyme -> rejeté (403)
        mockMvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isForbidden());

        // ADMIN -> 204
        mockMvc.perform(delete(BASE + "/" + id)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
        assertThat(joueurRepository.count()).isZero();
    }

    @Test
    @DisplayName("Payload invalide (champs obligatoires absents) -> 400")
    void payloadInvalideRenvoie400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\": \"Seulement le nom\"}"))
                .andExpect(status().isBadRequest());
        assertThat(joueurRepository.count()).isZero();
    }
}
