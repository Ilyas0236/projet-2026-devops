package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.*;
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
 * B.5 — Preuves serveur de la messagerie et des annonces.
 *
 * 1. un joueur ne peut écrire qu'au staff de SA catégorie (403 sinon) ;
 * 2. le staff ne peut écrire qu'aux joueurs de SA catégorie ;
 * 3. une conversation n'est lisible que par ses participants ;
 * 4. les annonces club sont visibles de tous, celles de catégorie
 *    filtrées par la catégorie du lecteur (côté serveur) ;
 * 5. un joueur ne peut pas publier d'annonce (403).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:messaging;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Note : H2 MODE=PostgreSQL embarqué pour la CI sans démon Docker ;
        // revalider sur PostgreSQL réel au déploiement.
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.notification-service-uri=http://localhost:1"
})
@AutoConfigureMockMvc
class MessagingSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired AnnouncementRepository announcementRepository;

    /** Client HTTP réel pointant sur un port impossible : le mode
     *  best-effort doit avaler l'échec ; on le mocke pour vérifier. */
    @MockBean com.wydad.digital.sports.client.NotificationClient notificationClient;

    private Player joueur(Long uid, String nom) {
        return joueur(uid, nom, SportType.FOOTBALL, Category.U19);
    }

    private Player joueur(Long uid, String nom, SportType sport, Category cat) {
        return playerRepository.save(Player.builder()
                .userId(uid).fullName(nom)
                .sportType(sport).category(cat)
                .build());
    }

    private Staff staff(Long uid, String nom, SportType sport, Category cat) {
        return staffRepository.save(Staff.builder()
                .userId(uid).fullName(nom)
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(sport).assignedCategory(cat)
                .build());
    }

    private static final String EMAIL = "x-test@wydad.ma";

    @AfterEach
    void clean() {
        messageRepository.deleteAll();
        announcementRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    @Test
    void joueurNePeutEcrireQuAuStaffDeSaCategorie() throws Exception {
        joueur(601L, "Joueur Msg");
        staff(602L, "Coach Foot U19", SportType.FOOTBALL, Category.U19);
        staff(603L, "Coach Hand", SportType.HANDBALL, Category.PRO);

        // Au staff d'une autre catégorie → 403, rien écrit
        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "601")
                        .param("toUserId", "603")
                        .contentType("application/json")
                        .content("{\"content\":\"Bonjour coach\"}"))
                .andExpect(status().isForbidden());

        // Au staff de sa catégorie → 201
        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Id", "601")
                        .header("X-User-Role", "JOUEUR")
                        .param("toUserId", "602")
                        .contentType("application/json")
                        .content("{\"content\":\"Bonjour coach\"}"))
                .andExpect(status().isCreated());

        assertThat(messageRepository.findAll()).hasSize(1);
    }

    @Test
    void staffNePeutEcrireQuAuxJoueursDeSaCategorie() throws Exception {
        joueur(611L, "Joueur Foot");
        joueur(612L, "Joueur Hand", SportType.HANDBALL, Category.PRO);
        staff(613L, "Coach Foot U19", SportType.FOOTBALL, Category.U19);

        // Au joueur handball → 403
        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "613")
                        .param("toUserId", "612")
                        .contentType("application/json")
                        .content("{\"content\":\"Convocation\"}"))
                .andExpect(status().isForbidden());

        // Au joueur foot U19 → 201
        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "613")
                        .param("toUserId", "611")
                        .contentType("application/json")
                        .content("{\"content\":\"Présente-toi à 10h\"}"))
                .andExpect(status().isCreated());

        assertThat(messageRepository.findAll()).hasSize(1);
    }

    @Test
    void conversationLisibleParParticipantsUniquement() throws Exception {
        joueur(621L, "Joueur A");
        staff(622L, "Coach A", SportType.FOOTBALL, Category.U19);
        joueur(623L, "Espion");

        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "621")
                        .param("toUserId", "622")
                        .contentType("application/json")
                        .content("{\"content\":\"Message confidentiel\"}"))
                .andExpect(status().isCreated());

        // Participant → voit la conversation
        mvc.perform(get("/api/sports/messaging/conversation/622")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "621"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Non-participant → ne voit RIEN
        mvc.perform(get("/api/sports/messaging/conversation/622")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "623"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void annoncesFiltreesParCategorieDuLecteur() throws Exception {
        joueur(631L, "Joueur Foot U19");   // FOOTBALL / U19
        staff(632L, "Coach Foot U19", SportType.FOOTBALL, Category.U19);
        staff(633L, "Coach Hand PRO", SportType.HANDBALL, Category.PRO);

        // Annonce club (sans ciblage)
        mvc.perform(post("/api/sports/messaging/announcements")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "999")
                        .contentType("application/json")
                        .content("{\"title\":\"Match dimanche\",\"body\":\"Tout le club est convié\"}"))
                .andExpect(status().isCreated());

        // Annonce FOOTBALL/U19 par le coach
        mvc.perform(post("/api/sports/messaging/announcements")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "632")
                        .param("sportType", "FOOTBALL").param("category", "U19")
                        .contentType("application/json")
                        .content("{\"title\":\"Tenue\",\"body\":\"Tenue complète obligatoire\"}"))
                .andExpect(status().isCreated());

        // Le joueur voit : annonce club + annonce SA catégorie = 2
        mvc.perform(get("/api/sports/messaging/announcements")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "631"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Le coach handball PRO ne voit QUE l'annonce club
        mvc.perform(get("/api/sports/messaging/announcements")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "633"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void joueurNePeutPasPublierDAnnonce() throws Exception {
        joueur(641L, "Simple Joueur");

        mvc.perform(post("/api/sports/messaging/announcements")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "641")
                        .contentType("application/json")
                        .content("{\"title\":\"Fake\",\"body\":\"Fake\"}"))
                .andExpect(status().isForbidden());

        assertThat(announcementRepository.findAll()).isEmpty();
    }
}
