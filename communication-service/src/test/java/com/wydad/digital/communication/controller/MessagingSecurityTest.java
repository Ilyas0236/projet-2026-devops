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
    @MockBean com.wydad.digital.communication.service.MessageMediaService messageMediaService;

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

    // ─────────────────────── V2.4 — BATCH IDOR ──────────────────────────

    @Test
    void staffEcritABatchDeJoueursDeSaCategorie_OK() throws Exception {
        // 1 coach FOOTBALL/U19, 3 joueurs U19 de la même catégorie
        rosterStaff(651L, "Coach Foot U19", "FOOTBALL", "U19");
        rosterJoueur(652L, "Joueur 1", "FOOTBALL", "U19");
        rosterJoueur(653L, "Joueur 2", "FOOTBALL", "U19");
        rosterJoueur(654L, "Joueur 3", "FOOTBALL", "U19");

        mvc.perform(post("/api/sports/messaging/send-batch")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "651")
                        .contentType("application/json")
                        .content("{\"toUserIds\":[652,653,654],\"content\":\"Match samedi 15h\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)));

        assertThat(messageRepository.findAll()).hasSize(3);
    }

    @Test
    void staffEcritABatchIncluantJoueurAutreCategorie_403() throws Exception {
        // 1 coach FOOTBALL/U19 ; batch = 2 U19 + 1 U17
        // → AccessDeniedException "Vous ne pouvez écrire qu'aux joueurs de votre catégorie"
        // → 0 message persisté (rollback transactionnel)
        rosterStaff(661L, "Coach Foot U19", "FOOTBALL", "U19");
        rosterJoueur(662L, "Joueur U19-A", "FOOTBALL", "U19");
        rosterJoueur(663L, "Joueur U19-B", "FOOTBALL", "U19");
        rosterJoueur(664L, "Joueur U17", "FOOTBALL", "U17");

        mvc.perform(post("/api/sports/messaging/send-batch")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "661")
                        .contentType("application/json")
                        .content("{\"toUserIds\":[662,663,664],\"content\":\"Test IDOR\"}"))
                .andExpect(status().isForbidden());

        // Atomicité : 0 message persisté (pas de demi-envoi)
        assertThat(messageRepository.findAll()).isEmpty();
    }

    // ─────────── V2.5 — STAFF multi-cibles (STAFF/ENTRAINEUR) ────────────

    /**
     * Le test de l'extension STAFF→STAFF/ENTRAINEUR même discipline
     * repose sur un mock de {@code AuthUserInfoClient} (le RosterClient
     * ne lit que les fiches player/staff). On ne mocke que les méthodes
     * consommées, le reste reste best-effort.
     */
    @MockBean com.wydad.digital.communication.client.AuthUserInfoClient authUserInfoClient;

    @Test
    void staffEcritAStaffMemeDiscipline_OK() throws Exception {
        // 1 staff FOOTBALL, 1 autre STAFF de la même discipline (pas de fiche
        // roster côté RosterClient — lu via AuthUserInfoClient).
        rosterStaff(671L, "Coach A", "FOOTBALL", "SENIOR");
        when(authUserInfoClient.getRoleAndDiscipline(672L))
                .thenReturn(new com.wydad.digital.communication.client.AuthUserInfoClient.UserRoleDiscipline(
                        672L, "autre@wac.ma", "STAFF", "FOOTBALL"));

        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "671")
                        .param("toUserId", "672")
                        .contentType("application/json")
                        .content("{\"content\":\"Hello collègue\"}"))
                .andExpect(status().isCreated());

        assertThat(messageRepository.findAll()).hasSize(1);
    }

    @Test
    void staffEcritAStaffAutreDiscipline_403() throws Exception {
        // 1 staff FOOTBALL, 1 STAFF BASKETBALL → IDOR strict = 403.
        rosterStaff(681L, "Coach Foot", "FOOTBALL", "SENIOR");
        when(authUserInfoClient.getRoleAndDiscipline(682L))
                .thenReturn(new com.wydad.digital.communication.client.AuthUserInfoClient.UserRoleDiscipline(
                        682L, "autre@wac.ma", "STAFF", "BASKETBALL"));

        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "681")
                        .param("toUserId", "682")
                        .contentType("application/json")
                        .content("{\"content\":\"cross-discipline\"}"))
                .andExpect(status().isForbidden());

        assertThat(messageRepository.findAll()).isEmpty();
    }

    @Test
    void staffEcritAEntraineurMemeDiscipline_OK() throws Exception {
        // STAFF écrit à un ENTRAINEUR de la même discipline = OK.
        rosterStaff(691L, "Coach A", "FOOTBALL", "SENIOR");
        when(authUserInfoClient.getRoleAndDiscipline(692L))
                .thenReturn(new com.wydad.digital.communication.client.AuthUserInfoClient.UserRoleDiscipline(
                        692L, "entraineur@wac.ma", "ENTRAINEUR", "FOOTBALL"));

        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "691")
                        .param("toUserId", "692")
                        .contentType("application/json")
                        .content("{\"content\":\"On se cale pour samedi ?\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void staffEcritAEntraineurAutreDiscipline_403() throws Exception {
        // STAFF écrit à un ENTRAINEUR d'une AUTRE discipline = 403.
        rosterStaff(701L, "Coach Foot", "FOOTBALL", "SENIOR");
        when(authUserInfoClient.getRoleAndDiscipline(702L))
                .thenReturn(new com.wydad.digital.communication.client.AuthUserInfoClient.UserRoleDiscipline(
                        702L, "entraineur@wac.ma", "ENTRAINEUR", "BASKETBALL"));

        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "701")
                        .param("toUserId", "702")
                        .contentType("application/json")
                        .content("{\"content\":\"cross-discipline\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffEcritAPresidentDeSaDiscipline_OK() throws Exception {
        // V2.5 — STAFF/ENTRAINEUR écrit au PRÉSIDENT de SA discipline = OK
        // (canal président dédié, symétrique du cas joueur C.21).
        rosterStaff(721L, "Coach Foot", "FOOTBALL", "SENIOR");
        when(authUserInfoClient.getRoleAndDiscipline(722L))
                .thenReturn(new com.wydad.digital.communication.client.AuthUserInfoClient.UserRoleDiscipline(
                        722L, "president-foot@wac.ma", "PRESIDENT", "FOOTBALL"));

        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "721")
                        .param("toUserId", "722")
                        .contentType("application/json")
                        .content("{\"content\":\"M. le président, rapport d entraînement\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void staffEcritAPresidentAutreDiscipline_403() throws Exception {
        // V2.5 — STAFF écrit au PRÉSIDENT d'une AUTRE discipline = 403
        // (le canal président est strictement limité à SA section).
        rosterStaff(731L, "Coach Foot", "FOOTBALL", "SENIOR");
        when(authUserInfoClient.getRoleAndDiscipline(732L))
                .thenReturn(new com.wydad.digital.communication.client.AuthUserInfoClient.UserRoleDiscipline(
                        732L, "president-basket@wac.ma", "PRESIDENT", "BASKETBALL"));

        mvc.perform(post("/api/sports/messaging/send")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "731")
                        .param("toUserId", "732")
                        .contentType("application/json")
                        .content("{\"content\":\"cross-discipline president\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamChat_messageAvecMedia_viaUploadPrealable_OK() throws Exception {
        // V2.5 — le front upload sur /upload puis POST /team-chat/.../messages
        // avec attachmentPublicId. MessageMediaService est mocké en @MockBean
        // (on n'atteint pas /attachment ici, donc pas besoin de stub signedUrl).
        rosterStaff(711L, "Coach Foot", "FOOTBALL", "SENIOR");

        mvc.perform(post("/api/sports/team-chat/FOOTBALL/SENIOR/messages")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "711")
                        .contentType("application/json")
                        .content("{\"content\":\"Photo du jour\","
                                + "\"attachmentPublicId\":\"abc123\","
                                + "\"attachmentSecureUrl\":\"https://example.com/p.png\","
                                + "\"attachmentResourceType\":\"image\","
                                + "\"attachmentFileName\":\"p.png\","
                                + "\"attachmentSizeBytes\":12345}"))
                .andExpect(status().isCreated());
    }
}
