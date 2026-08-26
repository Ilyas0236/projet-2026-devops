package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Convocation;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/**
 * B.3 / B.3.a — Preuves serveur de l'espace joueur.
 *
 * L'identité est injectée via les en-têtes X-User-* exactement comme la
 * gateway le fait en production : le test couvre la chaîne complète
 * (filtre UserContextFilter -> SecurityContext -> ownership service).
 *
 * 1. un joueur ne voit que SES convocations ;
 * 2. répondre à la convocation d'un autre joueur → 403 et réponse non écrite ;
 * 3. ABSENT sans justification → 400 ;
 * 4. seul le staff de la catégorie du joueur peut convoquer (403 sinon),
 *    et la convocation déclenche une notification (mock vérifié) ;
 * 5. l'édition de profil par le joueur ignore numéro/poste/catégorie.
 *
 * Phase 3 :
 * 6. convocation GROUPÉE (« liste cochable ») : N joueurs en un appel,
 *    rejets motivés pour doublon/INAPTE, notifications par joueur créé ;
 * 7. staff hors catégorie → 403 sur l'appel groupé, rien créé ;
 * 8. accusé de lecture : le joueur marque SA convocation, ownership strict
 *    (403 sinon), idempotent ;
 * 9. vue entraîneur : réponses + lecture par séance, compteurs exacts.
 *
 * Phase 3 — médias tactiques :
 * 10. envoi multipart à UN joueur (ownership vérifié), type déduit du MIME,
 *     notification au destinataire ;
 * 11. envoi « toute l'équipe » : tous les joueurs de la catégorie notifiés,
 *     chacun voit le média dans sa boîte de réception.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:playerspace;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
class PlayerSpaceSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired ConvocationRepository convocationRepository;
    @Autowired PlayerDocumentRepository playerDocumentRepository;

    /** Client HTTP réel pointant sur un port impossible : le mode
     *  best-effort doit avaler l'échec ; on le mocke pour VÉRIFIER l'appel. */
    @MockBean com.wydad.digital.sports.client.NotificationClient notificationClient;

    private Player joueur(Long uid, String nom) {
        return playerRepository.save(Player.builder()
                .userId(uid).fullName(nom)
                .sportType(SportType.FOOTBALL).category(Category.SENIOR)
                .position("Milieu").jerseyNumber(8)
                .build());
    }

    private Session seance(LocalDateTime date) {
        return sessionRepository.save(Session.builder()
                .title("Entraînement collectif").location("Complexe Mohammed V")
                .sessionDate(date).sportType(SportType.FOOTBALL).category(Category.SENIOR)
                .createdByStaffId(99L)
                .build());
    }

    private Convocation convoque(Long uid, Session s) {
        return convocationRepository.save(Convocation.builder()
                .joueurUserId(uid).session(s)
                .sportType(SportType.FOOTBALL).category(Category.SENIOR)
                .createdByStaffId(99L).build());
    }

    private static final String EMAIL = "x-test@wydad.ma";

    @AfterEach
    void clean() {
        convocationRepository.deleteAll();
        playerDocumentRepository.deleteAll();
        sessionRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    @Test
    void unJoueurNeVoitQueSesConvocations() throws Exception {
        Player moi = joueur(301L, "Joueur A");
        Player autre = joueur(302L, "Joueur B");
        Session s = seance(LocalDateTime.now().plusDays(2));
        convoque(moi.getUserId(), s);
        convoque(autre.getUserId(), s);

        mvc.perform(get("/api/sports/my-space/convocations")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", moi.getUserId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionId").value(s.getId().intValue()))
                .andExpect(jsonPath("$[0].responseStatus", nullValue()));
    }

    @Test
    void repondreALaConvocationDUnAutreJoueurEstRefuse() throws Exception {
        Player autre = joueur(303L, "Joueur C");
        Session s = seance(LocalDateTime.now().plusDays(3));
        Convocation c = convoque(autre.getUserId(), s);

        // Le joueur 999 tente de répondre à la convocation du joueur 303.
        mvc.perform(post("/api/sports/my-space/convocations/" + c.getId() + "/respond")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "999")
                        .contentType("application/json")
                        .content("{\"status\":\"CONFIRME\"}"))
                .andExpect(status().isForbidden());

        assertThat(convocationRepository.findById(c.getId()).orElseThrow()
                .getResponseStatus()).isNull();
    }

    @Test
    void absentSansJustificationEstRejete() throws Exception {
        joueur(304L, "Joueur D");
        Session s = seance(LocalDateTime.now().plusDays(4));
        Convocation c = convoque(304L, s);

        mvc.perform(post("/api/sports/my-space/convocations/" + c.getId() + "/respond")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "304")
                        .contentType("application/json")
                        .content("{\"status\":\"ABSENT\"}"))
                .andExpect(status().isBadRequest());

        assertThat(convocationRepository.findById(c.getId()).orElseThrow()
                .getResponseStatus()).isNull();
    }

    @Test
    void seulLeStaffDeLaCategoriePeutConvoquer_etNotificationEmise() throws Exception {
        joueur(305L, "Joueur E");
        Session s = seance(LocalDateTime.now().plusDays(5));

        staffRepository.save(Staff.builder()
                .userId(400L).fullName("Coach Hand")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.BASKETBALL).assignedCategory(Category.U17)
                .build());

        mvc.perform(post("/api/sports/my-space/staff/convocations")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "400")
                        .param("joueurUserId", "305")
                        .param("sessionId", s.getId().toString()))
                .andExpect(status().isForbidden());

        staffRepository.save(Staff.builder()
                .userId(401L).fullName("Coach Foot U19")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.SENIOR)
                .build());

        mvc.perform(post("/api/sports/my-space/staff/convocations")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "401")
                        .param("joueurUserId", "305")
                        .param("sessionId", s.getId().toString()))
                .andExpect(status().isCreated());

        assertThat(convocationRepository.findByJoueurUserIdOrderBySession_SessionDateAsc(305L))
                .hasSize(1);

        verify(notificationClient).notifyUser(
                org.mockito.ArgumentMatchers.eq(305L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("Nouvelle convocation"),
                org.mockito.ArgumentMatchers.contains("convoqué"),
                org.mockito.ArgumentMatchers.eq("/joueur/dashboard"));
    }

    @Test
    void joueurNePeutPasModifierNumeroNiPoste() throws Exception {
        joueur(306L, "Joueur F");

        mvc.perform(MockMvcRequestBuilders.put("/api/sports/my-space/profile")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "306")
                        .contentType("application/json")
                        .content("{\"height\":180,\"weight\":75,\"jerseyNumber\":10,\"position\":\"Attaquant\",\"category\":\"SENIOR\"}"))
                .andExpect(status().isOk());

        Player p = playerRepository.findByUserId(306L).orElseThrow();
        assertThat(p.getHeight()).isEqualTo(180.0);          // champ autorisé appliqué
        assertThat(p.getJerseyNumber()).isEqualTo(8);         // champ interdit inchangé
        assertThat(p.getPosition()).isEqualTo("Milieu");      // champ interdit inchangé
        assertThat(p.getCategory()).isEqualTo(Category.SENIOR); // champ interdit inchangé
    }

    // ─────────────────────── Phase 3 — groupage & suivi ───────────────────────

    @Test
    void convocationGroupee_creePourChaqueJoueur_etRejetteDoublon() throws Exception {
        joueur(311L, "Joueur G");
        joueur(312L, "Joueur H");
        Session s = seance(LocalDateTime.now().plusDays(6));

        staffRepository.save(Staff.builder()
                .userId(402L).fullName("Coach Foot U19 bis")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.SENIOR)
                .build());

        // 311 + 312 créés ; 311 une 2e fois → rejet motivé (doublon).
        mvc.perform(post("/api/sports/my-space/staff/convocations/batch")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "402")
                        .contentType("application/json")
                        .content("{\"sessionId\":" + s.getId() + ",\"joueurUserIds\":[311,312,311]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.convocations", hasSize(2)))
                .andExpect(jsonPath("$.rejected", hasSize(1)))
                .andExpect(jsonPath("$.rejected[0].joueurUserId").value(311));

        assertThat(convocationRepository.findBySession_IdOrderByCreatedAtAsc(s.getId())).hasSize(2);
        // Une notification par convocation réellement créée (pas pour le doublon).
        verify(notificationClient, org.mockito.Mockito.times(2)).notifyUser(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("Nouvelle convocation"),
                org.mockito.ArgumentMatchers.contains("convoqué"),
                org.mockito.ArgumentMatchers.eq("/joueur/dashboard"));
    }

    @Test
    void convocationGroupee_horsCategorie_403_etRienCree() throws Exception {
        joueur(313L, "Joueur I");
        Session s = seance(LocalDateTime.now().plusDays(7));

        staffRepository.save(Staff.builder()
                .userId(403L).fullName("Coach Hand hors catégorie")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.BASKETBALL).assignedCategory(Category.U17)
                .build());

        mvc.perform(post("/api/sports/my-space/staff/convocations/batch")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "403")
                        .contentType("application/json")
                        .content("{\"sessionId\":" + s.getId() + ",\"joueurUserIds\":[313]}"))
                .andExpect(status().isForbidden());

        assertThat(convocationRepository.findBySession_IdOrderByCreatedAtAsc(s.getId())).isEmpty();
    }

    @Test
    void accuseDeLecture_ownerSeul_etIdempotent() throws Exception {
        Player moi = joueur(314L, "Joueur J");
        Session s = seance(LocalDateTime.now().plusDays(8));
        Convocation c = convoque(moi.getUserId(), s);

        // Un AUTRE joueur tente de marquer la convocation de 314 → 403.
        mvc.perform(post("/api/sports/my-space/convocations/" + c.getId() + "/read")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "999"))
                .andExpect(status().isForbidden());
        assertThat(convocationRepository.findById(c.getId()).orElseThrow().getReadAt()).isNull();

        // Le propriétaire marque : readAt posé.
        mvc.perform(post("/api/sports/my-space/convocations/" + c.getId() + "/read")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", moi.getUserId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        // Re-lecture idempotente : readAt inchangé.
        java.time.LocalDateTime first =
                convocationRepository.findById(c.getId()).orElseThrow().getReadAt();
        mvc.perform(post("/api/sports/my-space/convocations/" + c.getId() + "/read")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", moi.getUserId().toString()))
                .andExpect(status().isOk());
        assertThat(convocationRepository.findById(c.getId()).orElseThrow().getReadAt())
                .isEqualTo(first);
    }

    @Test
    void vueEntraineur_reponsesEtLecture_parSeance() throws Exception {
        Player a = joueur(315L, "Joueur K");
        Player b = joueur(316L, "Joueur L");
        Session s = seance(LocalDateTime.now().plusDays(9));
        Convocation ca = convoque(a.getUserId(), s);
        Convocation cb = convoque(b.getUserId(), s);

        // A lit et confirme ; B ne fait rien.
        ca.setReadAt(LocalDateTime.now().minusHours(1));
        ca.setResponseStatus(Convocation.ResponseStatus.CONFIRME);
        ca.setRespondedAt(LocalDateTime.now());
        convocationRepository.save(ca);

        staffRepository.save(Staff.builder()
                .userId(404L).fullName("Coach Foot U19 ter")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.SENIOR)
                .build());

        mvc.perform(get("/api/sports/my-space/staff/sessions/" + s.getId() + "/responses")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].joueurName").value("Joueur K"));

        mvc.perform(get("/api/sports/my-space/staff/sessions/" + s.getId() + "/responses/summary")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.confirmed").value(1))
                .andExpect(jsonPath("$.pending").value(1));

        assertThat(cb.getReadAt()).isNull();
    }

    // ─────────────────────── Phase 3 — médias tactiques ───────────────────────

    @Test
    void mediaVersUnJoueur_uploadEtNotification() throws Exception {
        Player moi = joueur(321L, "Joueur M");
        Session s = seance(LocalDateTime.now().plusDays(10));

        staffRepository.save(Staff.builder()
                .userId(405L).fullName("Coach Média U19")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.SENIOR)
                .build());

        byte[] pdf = "%PDF-1.4 test tactique".getBytes();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/sports/my-space/staff/media")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "analyse.pdf", "application/pdf", pdf))
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "405")
                        .param("title", "Analyse adversaire")
                        .param("message", "À regarder avant vendredi")
                        .param("joueurUserId", moi.getUserId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("DOCUMENT"))
                .andExpect(jsonPath("$.title").value("Analyse adversaire"));

        // Le joueur voit le média dans SA boîte de réception.
        mvc.perform(get("/api/sports/my-space/documents")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", moi.getUserId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message").value("À regarder avant vendredi"));

        verify(notificationClient).notifyUser(
                org.mockito.ArgumentMatchers.eq(moi.getUserId()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.contains("média"),
                org.mockito.ArgumentMatchers.contains("Analyse"),
                org.mockito.ArgumentMatchers.eq("/joueur/dashboard"));
    }

    @Test
    void mediaTouteEquipe_tousLesJoueursDeLaCategorieNotifies() throws Exception {
        joueur(322L, "Joueur N");
        joueur(323L, "Joueur O");
        // Un joueur d'une AUTRE catégorie ne doit PAS recevoir le média équipe.
        playerRepository.save(Player.builder()
                .userId(324L).fullName("Joueur P U17")
                .sportType(SportType.FOOTBALL).category(Category.U17)
                .position("Gardien").jerseyNumber(1)
                .build());

        staffRepository.save(Staff.builder()
                .userId(406L).fullName("Coach Vidéo U19")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.SENIOR)
                .build());

        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/sports/my-space/staff/media")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "tableau.png", "image/png", png))
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "406")
                        .param("title", "Placement défensif")
                        .param("wholeTeam", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("PHOTO"));

        // Chaque joueur U19 voit le média ; le joueur U17 non.
        for (long uid : new long[]{322, 323}) {
            mvc.perform(get("/api/sports/my-space/documents")
                            .header("X-User-Email", EMAIL)
                            .header("X-User-Role", "JOUEUR")
                            .header("X-User-Id", String.valueOf(uid)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
        mvc.perform(get("/api/sports/my-space/documents")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "324"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Le staff retrouve son envoi dans son historique.
        mvc.perform(get("/api/sports/my-space/staff/media/sent")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "406"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
