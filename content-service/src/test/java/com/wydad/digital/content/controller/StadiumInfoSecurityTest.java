package com.wydad.digital.content.controller;

import com.wydad.digital.content.repository.ClubSettingRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Page « Stade » — preuves serveur de la clé de configuration
 * "stadium_info" (ClubSettingController, même mécanique que social_links).
 * Conception selon ISTQB Foundation Level :
 *
 *  - Partition d'équivalence sur le rôle {anonyme, STAFF, ADMIN} ;
 *  - Table de décision sur la valeur :
 *      R1: scalaire nu ("abc") -> 400 (un paramètre club est objet/tableau)
 *      R2: objet complet valide -> 200 persisté
 *      R3: upsert répété -> mise à jour (pas de doublon)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content_stadium;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class StadiumInfoSecurityTest {

    private static final String BASE = "/api/content/settings";

    private static final String STADIUM_JSON = """
            {"name": "Stade Mohammed-V", "city": "Casablanca", \
            "capacity": 45300, "address": "Rue Ahmed Charci, Casablanca", \
            "accessInfo": "Lignes de tramway T2…", "history": "Inauguré en 1955…"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubSettingRepository clubSettingRepository;

    @BeforeEach
    void cleanBase() {
        clubSettingRepository.deleteAll();
    }

    private static String adminHeaders() {
        return null;
    }

    // ─── Partition d'équivalence : rôle ────────────────────────────────

    @Test
    @DisplayName("[EP-rôle] ADMIN écrit stadium_info -> 200, persistance réelle")
    void adminEcritStadiumInfo() throws Exception {
        mockMvc.perform(put(BASE + "/stadium_info")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STADIUM_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingKey").value("stadium_info"));

        // Lecture publique sans identité : la page /stade consomme cette clé.
        mockMvc.perform(get(BASE + "/stadium_info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stade Mohammed-V"))
                .andExpect(jsonPath("$.capacity").value(45300));
    }

    @Test
    @DisplayName("[EP-rôle] Écriture anonyme -> 403, rien persisté")
    void ecritureAnonymeRefusee() throws Exception {
        mockMvc.perform(put(BASE + "/stadium_info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STADIUM_JSON))
                .andExpect(status().isForbidden());
        assertThat(clubSettingRepository.count()).isZero();
    }

    @Test
    @DisplayName("[EP-rôle] Écriture STAFF -> 403 (réservé ADMIN)")
    void ecritureStaffRefusee() throws Exception {
        mockMvc.perform(put(BASE + "/stadium_info")
                        .header("X-User-Id", "7")
                        .header("X-User-Email", "staff@test.ma")
                        .header("X-User-Role", "STAFF")
                        .content(STADIUM_JSON)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        assertThat(clubSettingRepository.count()).isZero();
    }

    // ─── Table de décision sur la valeur ───────────────────────────────

    @Test
    @DisplayName("[TD-R1] scalaire nu refusé -> 400")
    void scalaireNuRefuse400() throws Exception {
        mockMvc.perform(put(BASE + "/stadium_info")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"juste une chaine\""))
                .andExpect(status().isBadRequest());
        assertThat(clubSettingRepository.count()).isZero();
    }

    @Test
    @DisplayName("[TD-R3] upsert répété -> mise à jour sans doublon")
    void upsertSansDoublon() throws Exception {
        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(put(BASE + "/stadium_info")
                            .header("X-User-Id", "1")
                            .header("X-User-Email", "admin@test.ma")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(i == 1 ? STADIUM_JSON
                                    : "{\"name\": \"Autre stade\", \"capacity\": 1000}"))
                    .andExpect(status().isOk());
        }
        assertThat(clubSettingRepository.count()).isEqualTo(1);
        mockMvc.perform(get(BASE + "/stadium_info"))
                .andExpect(jsonPath("$.name").value("Autre stade"));
    }
}
