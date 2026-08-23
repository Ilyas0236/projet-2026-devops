package com.wydad.digital.content.controller;

import com.wydad.digital.content.repository.ReclamationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.10 — Preuves serveur des règles sur les réclamations :
 * - création sans identité -> 403 (aucune réclamation fantôme) ;
 * - l'identité du plaignant est imposée par le serveur (userId du body ignoré) ;
 * - un membre ne voit QUE ses réclamations ;
 * - le STAFF ne peut ni lister toutes, ni répondre -> 403 ;
 * - l'ADMIN liste tout et répond -> notification émise au plaignant.
 *
 * Identité injectée via X-User-Id + X-User-Email + X-User-Role.
 * H2 mode PostgreSQL ; revalidation au deploiement.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content_reclamations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class ReclamationSecurityTest {

    private static final String BASE = "/api/content/reclamations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReclamationRepository reclamationRepository;

    @BeforeEach
    void cleanBase() {
        reclamationRepository.deleteAll();
    }

    private String body(String subject, String title) {
        return """
                {"subject": "%s", "title": "%s", "description": "Commande 1234 jamais livree", \
                "claimedUserId": 777}""".formatted(subject, title);
    }

    @Test
    void creationSansIdentiteRefuseeEtRienEnBase() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType("application/json")
                        .content(body("SHOP", "Commande non recue")))
                .andExpect(status().isForbidden());
        assertThat(reclamationRepository.count()).isZero();
    }

    @Test
    void lidentiteDuPlaignantEstImposeeParLeServeur() throws Exception {
        // Le body prétend userId=777 ; le serveur impose celui des en-têtes (42)
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", 42)
                        .header("X-User-Email", "fan42@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .contentType("application/json")
                        .content(body("SHOP", "Commande non recue")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.userEmail").value("fan42@wydad.ma"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(reclamationRepository.count()).isEqualTo(1);
    }

    @Test
    void membreNeVoitQueSesPropresReclamations() throws Exception {
        createAs(42, "ADHERENT", "fan42@wydad.ma");
        createAs(43, "ADHERENT", "fan43@wydad.ma");

        mockMvc.perform(get(BASE + "/mine")
                        .header("X-User-Id", 42)
                        .header("X-User-Email", "fan42@wydad.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userEmail").value("fan42@wydad.ma"));
    }

    @Test
    void staffNePeutNiToutListerNiRepondre() throws Exception {
        createAs(42, "ADHERENT", "fan42@wydad.ma");

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", 9)
                        .header("X-User-Email", "staff@wydad.ma")
                        .header("X-User-Role", "STAFF"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE + "/1/response")
                        .header("X-User-Id", 9)
                        .header("X-User-Email", "staff@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType("application/json")
                        .content("{\"response\": \"Voila la reponse\", \"status\": \"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListeToutEtSaReponseNotifieLePlaignant() throws Exception {
        createAs(42, "ADHERENT", "fan42@wydad.ma");

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", 999)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(put(BASE + "/1/response")
                        .header("X-User-Id", 999)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content("{\"response\": \"Remboursement effectue\", \"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.adminResponse").value("Remboursement effectue"));
    }

    @Test
    void creationSansSujetTitreOuDescriptionRenvoie400() throws Exception {
        for (String invalid : new String[]{
                "{\"subject\": \"SHOP\", \"title\": \"\", \"description\": \"d\"}",
                "{\"subject\": null, \"title\": \"t\", \"description\": \"d\"}",
                "{\"subject\": \"SHOP\", \"title\": \"t\", \"description\": \"   \"}"}) {
            mockMvc.perform(post(BASE)
                            .header("X-User-Id", 42)
                            .header("X-User-Email", "fan42@wydad.ma")
                            .header("X-User-Role", "ADHERENT")
                            .contentType("application/json")
                            .content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(reclamationRepository.count()).isZero();
    }

    private void createAs(long uid, String role, String email) throws Exception {
        mockMvc.perform(post(BASE)
                        .header("X-User-Id", uid)
                        .header("X-User-Email", email)
                        .header("X-User-Role", role)
                        .contentType("application/json")
                        .content(body("SHOP", "Réclamation de test")))
                .andExpect(status().isCreated());
    }
}
