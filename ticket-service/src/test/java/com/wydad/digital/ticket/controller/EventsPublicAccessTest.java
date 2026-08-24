package com.wydad.digital.ticket.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1 — Décorticage front ↔ back : le menu public expose « Billetterie » à
 * un visiteur anonyme, mais GET /api/ticket/events était derrière
 * anyRequest().authenticated() -> 403 pour le fan non connecté.
 *
 * Correction : catalogue d'événements en lecture publique (donnée non
 * personnelle). Table de décision ISTQB (rôle × route) :
 *
 *   Rôle \ Route   | GET /events | GET /events/upcoming | POST /events | GET /tickets/user/{id}
 *   ---------------|-------------|----------------------|--------------|-----------------------
 *   Anonyme        | 200 (NOUVEAU)| 200 (NOUVEAU)       |     403      |          403
 *   ADHERENT       | 200         | 200                  |     403      |          200 (soi)
 *   ADMIN          | 200         | 200                  |     201      |          200
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventspublic;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class EventsPublicAccessTest {

    private static final String BASE = "/api/ticket";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("[TD] Anonyme lit les événements -> 200 (page /billetterie publique)")
    void anonymeLitEvenements() throws Exception {
        mockMvc.perform(get(BASE + "/events"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[TD] Anonyme lit /events/upcoming -> 200")
    void anonymeLitUpcoming() throws Exception {
        mockMvc.perform(get(BASE + "/events/upcoming"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[TD] Événement inexistant en anonyme -> 404 mais PAS 403")
    void anonymeEvenementInexistant() throws Exception {
        mockMvc.perform(get(BASE + "/events/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[TD] Création d'événement anonyme toujours refusée -> 403")
    void anonymeCreationRefusee() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(BASE + "/events")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[TD] Billets d'un utilisateur en anonyme toujours refusé -> 403")
    void anonymeBilletsRefuses() throws Exception {
        mockMvc.perform(get(BASE + "/tickets/user/42"))
                .andExpect(status().isForbidden());
    }
}
