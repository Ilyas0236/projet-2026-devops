package com.wydad.digital.communication.controller;

import com.wydad.digital.communication.client.RosterClient;
import com.wydad.digital.communication.repository.AnnouncementRepository;
import com.wydad.digital.communication.repository.MessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.5 — Preuves serveur de la messagerie et des annonces (adaptation du
 * test sports-service : le roster est MOCKÉ via {@link RosterClient}
 * puisque l'adhésion vit désormais dans sports-service).
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
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class MessagingSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired MessageRepository messageRepository;
    @Autowired AnnouncementRepository announcementRepository;

    /** Clients HTTP réels pointant sur des ports impossibles : le mode
     *  best-effort doit avaler l'échec ; on les mocke pour contrôler le roster. */
    @MockBean com.wydad.digital.communication.client.NotificationClient notificationClient;
    @MockBean RosterClient rosterClient;

    private static final String EMAIL = "x-test@wydad.ma";

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
        messageRepository.deleteAll();
        announcementRepository.deleteAll();
    }

    @Test
    void joueurNePeutEcrireQuAuStaffDeSaCategorie() throws Exception {
        rosterJoueur(601L, "Joueur Msg", "FOOTBALL", "U19");
        rosterStaff(602L, "Coach Foot U19", "FOOTBALL", "U19");
        rosterStaff(603L, "Coach Hand", "HANDBALL", "PRO");

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
        rosterJoueur(611L, "Joueur Foot", "FOOTBALL", "U19");
        rosterJoueur(612L, "Joueur Hand", "HANDBALL", "PRO");
        rosterStaff(613L, "Coach Foot U19", "FOOTBALL", "U19");

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
                        .header("X-User-Id", "613")
                        .header("X-User-Role", "STAFF")
                        .param("toUserId", "611")
                        .contentType("application/json")
                        .content("{\"content\":\"Présente-toi à 10h\"}"))
                .andExpect(status().isCreated());

        assertThat(messageRepository.findAll()).hasSize(1);
    }

    @Test
    void conversationLisibleParParticipantsUniquement() throws Exception {
        rosterJoueur(621L, "Joueur A", "FOOTBALL", "U19");
        rosterStaff(622L, "Coach A", "FOOTBALL", "U19");
        rosterJoueur(623L, "Espion", "FOOTBALL", "U17");

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
        rosterJoueur(631L, "Joueur Foot U19", "FOOTBALL", "U19");
        rosterStaff(632L, "Coach Foot U19", "FOOTBALL", "U19");
        rosterStaff(633L, "Coach Hand PRO", "HANDBALL", "PRO");

        // Annonce club (sans ciblage) par l'admin
        mvc.perform(post("/api/sports/messaging/announcements")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "999")
                        .contentType("application/json")
                        .content("{\"title\":\"Match dimanche\",\"body\":\"Tout le club est convié\"}"))
                .andExpect(status().isCreated());

        // Annonce FOOTBALL/U19 par le coach — ciblage imposé à SON groupe
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
        rosterJoueur(641L, "Simple Joueur", "FOOTBALL", "U19");

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
