package com.wydad.digital.election;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1 — Décorticage front ↔ back : la page publique /sondages appelle
 * GET /api/polls/active dès l'arrivée du visiteur ; la consultation des
 * sondages ACTIFS est en lecture publique (donnée non personnelle).
 * Le VOTE et l'administration restent réservés.
 * Table de décision ISTQB (rôle × route) :
 *
 *   Rôle \ Route | GET /polls/active | POST /polls/{id}/vote | POST /polls
 *   -------------|-------------------|-----------------------|-------------
 *   Anonyme      | 200 (public)      |         403           |    403
 *   ADHERENT     | 200               |         200           |    403
 *   ADMIN        | 200               |         200           |    201
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pollpublic;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class PollPublicAccessTest {

    private static final String BASE = "/api/polls";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("[TD] Anonyme lit les sondages actifs -> 200 (page /sondages publique)")
    void anonymeLitSondagesActifs() throws Exception {
        mockMvc.perform(get(BASE + "/active"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[TD] Vote anonyme toujours refusé -> 403")
    void anonymeVoteRefuse() throws Exception {
        mockMvc.perform(post(BASE + "/1/vote")
                        .queryParam("optionIndex", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] Création de sondage anonyme toujours refusée -> 403")
    void anonymeCreationRefusee() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"?\",\"options\":[\"A\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] Membre connecté lit aussi les sondages actifs -> 200")
    void membreLitSondagesActifs() throws Exception {
        mockMvc.perform(get(BASE + "/active")
                        .with(user("fan@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isOk());
    }
}
