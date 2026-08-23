package com.wydad.digital.content.controller;

import com.wydad.digital.content.repository.ClubSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.9 — Preuves serveur des regles sur les reseaux sociaux officiels
 * (cle de configuration "social_links") :
 * - lecture publique SANS identite -> 200 (footer vitrine) ;
 * - ecriture sans identite -> 403 ;
 * - ecriture role STAFF -> 403 (reserves a l'ADMIN) ;
 * - ecriture ADMIN -> 200 et persistance reelle.
 *
 * Identite injectee via X-User-Email + X-User-Role (UserContextFilter :
 * les deux en-tetes requis). H2 mode PostgreSQL ; revalidation au deploiement.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content_social;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class SocialLinksSecurityTest {

    private static final String BASE = "/api/content/settings";

    private static final String SOCIAL_LINKS_JSON = """
            [{"platform": "FACEBOOK", "url": "https://facebook.com/wydad-officiel"}, \
            {"platform": "INSTAGRAM", "url": "https://instagram.com/wydad-officiel"}]""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubSettingRepository clubSettingRepository;

    @BeforeEach
    void cleanBase() {
        clubSettingRepository.deleteAll();
    }

    @Test
    void lecturePubliqueSansIdentiteRenvoie200() throws Exception {
        mockMvc.perform(put(BASE + "/social_links")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(SOCIAL_LINKS_JSON))
                .andExpect(status().isOk());

        // Le footer lit les liens sans aucune authentification
        mockMvc.perform(get(BASE + "/social_links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platform").value("FACEBOOK"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void ecritureSansIdentiteRefuseeEtRienEnBase() throws Exception {
        mockMvc.perform(put(BASE + "/social_links")
                        .contentType("application/json")
                        .content(SOCIAL_LINKS_JSON))
                .andExpect(status().isForbidden());
        assertThat(clubSettingRepository.count()).isZero();
    }

    @Test
    void ecritureAvecRoleStaffRefusee403() throws Exception {
        mockMvc.perform(put(BASE + "/social_links")
                        .header("X-User-Email", "staff@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType("application/json")
                        .content(SOCIAL_LINKS_JSON))
                .andExpect(status().isForbidden());
        assertThat(clubSettingRepository.count()).isZero();
    }

    @Test
    void adminPeutEnregistrerLesLiensOfficiels() throws Exception {
        mockMvc.perform(put(BASE + "/social_links")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(SOCIAL_LINKS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingKey").value("social_links"));

        assertThat(clubSettingRepository.count()).isEqualTo(1);
    }
}
