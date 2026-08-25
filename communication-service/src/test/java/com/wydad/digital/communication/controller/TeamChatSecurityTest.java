package com.wydad.digital.communication.controller;

import com.wydad.digital.communication.client.RosterClient;
import com.wydad.digital.communication.repository.TeamMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 — preuves serveur de la messagerie de GROUPE (canal REST,
 * mêmes règles que le canal WebSocket). Adaptation du test sports-service :
 * le roster est MOCKÉ via {@link RosterClient}.
 *
 * <ol>
 *   <li>un membre écrit dans le groupe de SA catégorie ;</li>
 *   <li>un joueur d'une autre catégorie est refusé (403) — l'adhésion
 *       est déduite du roster, impossible de s'inviter ;</li>
 *   <li>l'historique n'est lisible que par les membres du groupe ;</li>
 *   <li>message vide ou trop long rejeté (400) ;</li>
 *   <li>l'admin supervise sans fiche roster ;</li>
 *   <li>les membres du groupe sont listés pour l'en-tête du chat.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teamchat;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class TeamChatSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired TeamMessageRepository teamMessageRepository;

    /** Notifications best-effort : mockées, jamais appelées via le canal REST. */
    @MockBean com.wydad.digital.communication.client.NotificationClient notificationClient;
    @MockBean RosterClient rosterClient;

    private static final String EMAIL = "chat-test@wydad.ma";

    private void rosterJoueur(Long uid, String nom, String sport, String cat) {
        when(rosterClient.findMembership(uid)).thenReturn(
                new RosterClient.MembershipInfo(uid, sport, cat, "JOUEUR", nom));
    }

    private void rosterStaff(Long uid, String nom, String sport, String cat) {
        when(rosterClient.findMembership(uid)).thenReturn(
                new RosterClient.MembershipInfo(uid, sport, cat, "STAFF", nom));
    }

    @AfterEach
    void clean() {
        teamMessageRepository.deleteAll();
    }

    @Test
    void membreDuGroupePeutEnvoyerEtLire() throws Exception {
        rosterJoueur(701L, "Joueur A", "FOOTBALL", "U19");
        rosterStaff(702L, "Coach U19", "FOOTBALL", "U19");

        // Le coach écrit dans le groupe → 201
        mvc.perform(post("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "702")
                        .header("X-User-Role", "ENTRAINEUR")
                        .contentType("application/json")
                        .content("{\"content\":\"Concentration demain 10h\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderName").value("Coach U19"))
                .andExpect(jsonPath("$.content").value("Concentration demain 10h"));

        // Le joueur lit l'historique du groupe → voit le message
        mvc.perform(get("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].senderName").value("Coach U19"));
    }

    @Test
    void joueurHorsCategorieRefuse() throws Exception {
        rosterJoueur(711L, "Joueur Handball", "HANDBALL", "PRO");

        mvc.perform(post("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "711")
                        .header("X-User-Role", "JOUEUR")
                        .contentType("application/json")
                        .content("{\"content\":\"Je m'invite\"}"))
                .andExpect(status().isForbidden());

        // Lecture interdite elle aussi
        mvc.perform(get("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "711")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isForbidden());

        assertThat(teamMessageRepository.findAll()).isEmpty();
    }

    @Test
    void sansFicheRosterRefuse() throws Exception {
        // Rôle JOUEUR mais aucune fiche roster côté sports-service.
        when(rosterClient.findMembership(721L)).thenReturn(null);

        mvc.perform(post("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "721")
                        .header("X-User-Role", "JOUEUR")
                        .contentType("application/json")
                        .content("{\"content\":\"Bonjour\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void messageVideOuTropLongRejete() throws Exception {
        rosterJoueur(731L, "Joueur B", "FOOTBALL", "U19");

        // Vide → 400
        mvc.perform(post("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "731")
                        .header("X-User-Role", "JOUEUR")
                        .contentType("application/json")
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // > 500 caractères → 400
        String long500 = "x".repeat(501);
        mvc.perform(post("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "731")
                        .header("X-User-Role", "JOUEUR")
                        .contentType("application/json")
                        .content("{\"content\":\"" + long500 + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(teamMessageRepository.findAll()).isEmpty();
    }

    @Test
    void adminSuperviseSansFicheRoster() throws Exception {
        rosterJoueur(741L, "Joueur C", "FOOTBALL", "U19");
        rosterStaff(742L, "Coach U19", "FOOTBALL", "U19");
        when(rosterClient.findMembership(999L)).thenReturn(null); // admin sans fiche

        // L'admin peut lire (supervision)
        mvc.perform(get("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Et écrire s'il veut (règle identique messagerie privée).
        // resolveName : pas de fiche → fallback « Administration ».
        mvc.perform(post("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content("{\"content\":\"Message direction\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderName").value("Administration"));
    }

    @Test
    void membresDuGroupeListesPourLEnTete() throws Exception {
        rosterJoueur(751L, "Joueur D", "FOOTBALL", "U19");
        rosterStaff(752L, "Coach U19", "FOOTBALL", "U19");

        when(rosterClient.findGroupMembers("FOOTBALL", "U19")).thenReturn(List.of(
                new RosterClient.RosterMember(751L, "Joueur D", "JOUEUR"),
                new RosterClient.RosterMember(752L, "Coach U19", "STAFF")));

        mvc.perform(get("/api/sports/team-chat/FOOTBALL/U19/members")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "751")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fullName",
                        org.hamcrest.Matchers.is("Joueur D")))
                .andExpect(jsonPath("$[1].fullName",
                        org.hamcrest.Matchers.is("Coach U19")));
    }
}
