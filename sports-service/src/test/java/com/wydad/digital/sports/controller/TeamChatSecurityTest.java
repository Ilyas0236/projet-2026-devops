package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import com.wydad.digital.sports.repository.TeamMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 — preuves serveur de la messagerie de GROUPE (canal REST,
 * mêmes règles que le canal WebSocket) :
 *
 * <ol>
 *   <li>un membre écrit dans le groupe de SA catégorie ;</li>
 *   <li>un joueur d'une autre catégorie est refusé (403) — l'adhésion
 *       est déduite de la fiche, impossible de s'inviter ;</li>
 *   <li>l'historique n'est lisible que par les membres du groupe ;</li>
 *   <li>message vide ou trop long rejeté (400) ;</li>
 *   <li>les membres du groupe voient l'historique complet avec
 *       expéditeur horodaté.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teamchat;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.notification-service-uri=http://localhost:1"
})
@AutoConfigureMockMvc
class TeamChatSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired TeamMessageRepository teamMessageRepository;

    /** Notifications best-effort : mockées, jamais appelées via le canal REST. */
    @MockBean com.wydad.digital.sports.client.NotificationClient notificationClient;

    private static final String EMAIL = "chat-test@wydad.ma";

    private Player joueur(Long uid, String nom, SportType sport, Category cat) {
        return playerRepository.save(Player.builder()
                .userId(uid).fullName(nom).sportType(sport).category(cat).build());
    }

    private Staff staff(Long uid, String nom, SportType sport, Category cat) {
        return staffRepository.save(Staff.builder()
                .userId(uid).fullName(nom)
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(sport).assignedCategory(cat).build());
    }

    @AfterEach
    void clean() {
        teamMessageRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    @Test
    void membreDuGroupePeutEnvoyerEtLire() throws Exception {
        joueur(701L, "Joueur A", SportType.FOOTBALL, Category.U19);
        staff(702L, "Coach U19", SportType.FOOTBALL, Category.U19);

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
        joueur(711L, "Joueur Handball", SportType.HANDBALL, Category.PRO);

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
    void sansFicheSportiveRefuse() throws Exception {
        // Rôle JOUEUR mais aucune fiche player en base.
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
        joueur(731L, "Joueur B", SportType.FOOTBALL, Category.U19);

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
    void adminSuperviseMaisNePolluePasLeGroupe() throws Exception {
        joueur(741L, "Joueur C", SportType.FOOTBALL, Category.U19);
        staff(742L, "Coach U19", SportType.FOOTBALL, Category.U19);

        // L'admin peut lire (supervision)
        mvc.perform(get("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Et écrire s'il veut (règle identique messagerie privée)
        mvc.perform(post("/api/sports/team-chat/FOOTBALL/U19/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content("{\"content\":\"Message direction\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void membresDuGroupeListesPourLEnTete() throws Exception {
        joueur(751L, "Joueur D", SportType.FOOTBALL, Category.U19);
        staff(752L, "Coach U19", SportType.FOOTBALL, Category.U19);
        staff(753L, "Coach Hand", SportType.HANDBALL, Category.PRO); // hors groupe

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
