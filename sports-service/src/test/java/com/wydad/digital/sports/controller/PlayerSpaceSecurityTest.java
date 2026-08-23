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
                .sportType(SportType.FOOTBALL).category(Category.U19)
                .position("Milieu").jerseyNumber(8)
                .build());
    }

    private Session seance(LocalDateTime date) {
        return sessionRepository.save(Session.builder()
                .title("Entraînement collectif").location("Complexe Mohammed V")
                .sessionDate(date).sportType(SportType.FOOTBALL).category(Category.U19)
                .createdByStaffId(99L)
                .build());
    }

    private Convocation convoque(Long uid, Session s) {
        return convocationRepository.save(Convocation.builder()
                .joueurUserId(uid).session(s)
                .sportType(SportType.FOOTBALL).category(Category.U19)
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
                .sportType(SportType.FOOTBALL).assignedCategory(Category.U19)
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
        assertThat(p.getCategory()).isEqualTo(Category.U19); // champ interdit inchangé
    }
}
