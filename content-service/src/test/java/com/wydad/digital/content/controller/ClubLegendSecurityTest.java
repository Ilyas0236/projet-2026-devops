package com.wydad.digital.content.controller;

import com.wydad.digital.content.repository.ClubLegendRepository;
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
 * Hall of Fame — CRUD /api/content/legends. Conception selon ISTQB
 * Foundation Level (chap. 4) :
 *
 *  - Partition d'équivalence sur le PARAMÈTRE RÔLE :
 *      {anonyme, STAFF, ADMIN} -> {refusé, refusé, autorisé} ;
 *
 *  - Table de décision sur la CRÉATION :
 *      R1: name vide   -> 400
 *      R2: role vide   -> 400
 *      R3: yearFrom nul-> 400
 *      R4: tout valide -> 201
 *
 *  - Analyse aux limites sur les ANNÉES [1900 .. année courante] :
 *      invalides testées : 1899, année courante + 1 ;
 *      valides testées : 1900 (borne basse), 1937 (nominal), courante (haute) ;
 *      cohérence : yearTo < yearFrom -> 400.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content_legends;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class ClubLegendSecurityTest {

    private static final String BASE = "/api/content/legends";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubLegendRepository legendRepository;

    @BeforeEach
    void cleanBase() {
        legendRepository.deleteAll();
    }

    /** Nominal : Mustapha Bettache, légende historique du club. */
    private static String validBody() {
        return """
                {"name": "Mustapha Bettache", "nickname": "Betta", "role": "Attaquant",
                 "yearFrom": 1957, "yearTo": 1970,
                 "biography": "Buteur emblématique des années 60."}""";
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
        assertThat(legendRepository.count()).isZero();
    }

    @Test
    @DisplayName("[EP-rôle] Création par un STAFF -> 403 (réservé ADMIN)")
    void creationParStaffRefusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "7")
                        .header("X-User-Email", "staff@test.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
        assertThat(legendRepository.count()).isZero();
    }

    // ─── Table de décision création (R1..R4) ───────────────────────────

    @Test
    @DisplayName("[TD-R1] name manquant -> 400")
    void nomManquant400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"Attaquant\", \"yearFrom\": 1957}"))
                .andExpect(status().isBadRequest());
        assertThat(legendRepository.count()).isZero();
    }

    @Test
    @DisplayName("[TD-R2] role manquant -> 400")
    void roleManquant400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mustapha Bettache\", \"yearFrom\": 1957}"))
                .andExpect(status().isBadRequest());
        assertThat(legendRepository.count()).isZero();
    }

    @Test
    @DisplayName("[TD-R3] yearFrom manquant -> 400")
    void anneeDebutManquante400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mustapha Bettache\", \"role\": \"Attaquant\"}"))
                .andExpect(status().isBadRequest());
        assertThat(legendRepository.count()).isZero();
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
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        // Le public voit exactement la fiche saisie.
        mockMvc.perform(get(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mustapha Bettache"))
                .andExpect(jsonPath("$[0].nickname").value("Betta"))
                .andExpect(jsonPath("$[0].yearFrom").value(1957))
                .andExpect(jsonPath("$[0].yearTo").value(1970));

        // Mise à jour partielle puis désactivation : sort de l'affichage public.
        mockMvc.perform(put(BASE + "/" + id)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biography\": \"Légende absolue.\", \"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Analyse aux limites : années [1900 .. courant] ────────────────

    @Test
    @DisplayName("[BVA] yearFrom = 1899 (juste sous la borne) -> 400")
    void anneeAvant1900Refusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Joueur Ancien\", \"role\": \"Milieu\", \"yearFrom\": 1899}"))
                .andExpect(status().isBadRequest());
        assertThat(legendRepository.count()).isZero();
    }

    @Test
    @DisplayName("[BVA] yearFrom = année courante + 1 (futur) -> 400")
    void anneeFuturRefusee() throws Exception {
        int nextYear = java.time.Year.now().getValue() + 1;
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Recrue Future\", \"role\": \"Gardien\", \"yearFrom\": " + nextYear + "}"))
                .andExpect(status().isBadRequest());
        assertThat(legendRepository.count()).isZero();
    }

    @Test
    @DisplayName("[BVA] yearTo < yearFrom (incohérent) -> 400")
    void periodeIncoherenteRefusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Incohérent\", \"role\": \"Défenseur\", \"yearFrom\": 1990, \"yearTo\": 1980}"))
                .andExpect(status().isBadRequest());
        assertThat(legendRepository.count()).isZero();
    }

    @Test
    @DisplayName("[BVA] bornes valides : 1900 et année courante acceptées")
    void bornesValidesAcceptees() throws Exception {
        int currentYear = java.time.Year.now().getValue();

        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pionnier 1900\", \"role\": \"Milieu\", \"yearFrom\": 1900}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Actuel\", \"role\": \"Gardien\", \"yearFrom\": " + currentYear + "}"))
                .andExpect(status().isCreated());
        assertThat(legendRepository.count()).isEqualTo(2);
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
        assertThat(legendRepository.count()).isZero();
    }
}
