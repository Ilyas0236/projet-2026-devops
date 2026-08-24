package com.wydad.digital.content.controller;

import com.wydad.digital.content.repository.TrophyRepository;
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
 * Palmarès du club — CRUD /api/content/trophies. Conception des tests
 * selon ISTQB Foundation Level (chap. 4 : techniques boîte noire) :
 *
 *  - Partition d'équivalence sur le PARAMÈTRE RÔLE :
 *      {anonyme, JOUEUR, ADMIN} -> {refusé, refusé, autorisé} ;
 *
 *  - Table de décision sur la CRÉATION (règles R1..R4) :
 *      R1: title vide            -> 400
 *      R2: category vide         -> 400
 *      R3: season vide           -> 400
 *      R4: tout valide           -> 201
 *
 *  - Analyse aux limites sur COUNT (entier >= 1) :
 *      bornes invalides testées : 0, -5 ; borne valide : 1, valeur nominale 22.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content_trophies;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class TrophySecurityTest {

    private static final String BASE = "/api/content/trophies";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrophyRepository trophyRepository;

    @BeforeEach
    void cleanBase() {
        trophyRepository.deleteAll();
    }

    private static String validBody() {
        return """
                {"title": "Ligue des Champions CAF", "category": "FOOTBALL",
                 "season": "2022-2023", "count": 3}""";
    }

    // ─── Partition d'équivalence : rôle ────────────────────────────────

    @Test
    @DisplayName("[EP-rôle] Lecture publique sans identité -> 200")
    void lecturePubliqueSansIdentite() throws Exception {
        mockMvc.perform(get(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("[EP-rôle] Création anonyme -> 403, rien persisté")
    void creationAnonymeRefusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
        assertThat(trophyRepository.count()).isZero();
    }

    @Test
    @DisplayName("[EP-rôle] Création par un JOUEUR -> 403, rien persisté")
    void creationParJoueurRefusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
        assertThat(trophyRepository.count()).isZero();
    }

    // ─── Table de décision création (R1..R4) ───────────────────────────

    @Test
    @DisplayName("[TD-R1] title manquant -> 400")
    void titreManquant400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\": \"FOOTBALL\", \"season\": \"2022\"}"))
                .andExpect(status().isBadRequest());
        assertThat(trophyRepository.count()).isZero();
    }

    @Test
    @DisplayName("[TD-R2] category manquante -> 400")
    void categorieManquante400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Botola Pro\", \"season\": \"2022\"}"))
                .andExpect(status().isBadRequest());
        assertThat(trophyRepository.count()).isZero();
    }

    @Test
    @DisplayName("[TD-R3] season manquante -> 400")
    void saisonManquante400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Botola Pro\", \"category\": \"FOOTBALL\"}"))
                .andExpect(status().isBadRequest());
        assertThat(trophyRepository.count()).isZero();
    }

    @Test
    @DisplayName("[TD-R4] tout valide -> créé et servi publiquement")
    void creationValideServiePubliquement() throws Exception {
        String created = mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.count").value(3))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        // Le public (sans identité) voit exactement ce que l'ADMIN a saisi.
        mockMvc.perform(get(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Ligue des Champions CAF"))
                .andExpect(jsonPath("$[0].season").value("2022-2023"))
                .andExpect(jsonPath("$[0].count").value(3));

        // Mise à jour ADMIN puis désactivation : sort de l'affichage public.
        mockMvc.perform(put(BASE + "/" + id)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": 4, \"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Analyse aux limites : count ───────────────────────────────────

    @Test
    @DisplayName("[BVA] count = 0 (borne invalide juste sous 1) -> 400")
    void countZeroRefuse() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Botola Pro", "category": "FOOTBALL",
                                 "season": "2022", "count": 0}"""))
                .andExpect(status().isBadRequest());
        assertThat(trophyRepository.count()).isZero();
    }

    @Test
    @DisplayName("[BVA] count = -5 (hors domaine) -> 400")
    void countNegatifRefuse() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Botola Pro", "category": "FOOTBALL",
                                 "season": "2022", "count": -5}"""))
                .andExpect(status().isBadRequest());
        assertThat(trophyRepository.count()).isZero();
    }

    @Test
    @DisplayName("[BVA] count = 1 (borne valide minimale) -> accepté")
    void countUnAccepte() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Coupe du Trône", "category": "FOOTBALL",
                                 "season": "1970", "count": 1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(1));
    }

    // ─── Suppression ───────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression réservée ADMIN ; anonyme rejeté ; 204 réel")
    void suppressionReserveeAdmin() throws Exception {
        String created = mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE + "/" + id)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
        assertThat(trophyRepository.count()).isZero();
    }
}
