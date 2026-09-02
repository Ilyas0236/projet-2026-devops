package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.SessionRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fonctionnalité 3/6 — Planning d'entraînements : règles prouvées côte
 * serveur :
 * - création réservée STAFF / ADMIN : un ADHERENT (et un JOUEUR) recoivent
 *   403 et rien n'est persisté ;
 * - payload invalide (titre ou date manquant) -> 400 ;
 * - création STAFF -> 201, persistée, et CHAQUE joueur du groupe visé
 *   (sport + catégorie) reçoit une notification IN_APP ;
 * - un joueur d'une autre catégorie n'est PAS notifié.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessiontest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class SessionSecurityTest {

    private static final String URL = "/api/sports/sessions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private StaffRepository staffRepository;

    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void seedPlayers() {
        sessionRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
        // Deux joueurs FOOTBALL/U15 avec compte lié + un joueur U17 (autre groupe)
        playerRepository.save(Player.builder()
                .userId(101L).fullName("Joueur U15 A")
                .sportType(com.wydad.digital.sports.enums.SportType.FOOTBALL)
                .category(com.wydad.digital.sports.enums.Category.U15).build());
        playerRepository.save(Player.builder()
                .userId(102L).fullName("Joueur U15 B")
                .sportType(com.wydad.digital.sports.enums.SportType.FOOTBALL)
                .category(com.wydad.digital.sports.enums.Category.U15).build());
        playerRepository.save(Player.builder()
                .userId(103L).fullName("Joueur U17")
                .sportType(com.wydad.digital.sports.enums.SportType.FOOTBALL)
                .category(com.wydad.digital.sports.enums.Category.U17).build());
        // Phase 5 — le coach STAFF doit avoir une fiche encadrement rattachée
        // à son userId : createSession force sport/catégorie depuis SA fiche.
        staffRepository.save(Staff.builder()
                .userId(301L).fullName("Coach U15")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(com.wydad.digital.sports.enums.SportType.FOOTBALL)
                .assignedCategory(com.wydad.digital.sports.enums.Category.U15).build());
    }

    private static String body(String titre) {
        return """
                {"title": "%s", "description": "Seance technique",
                 "location": "Stade Mohammed V",
                 "sessionDate": "2026-09-01T18:00:00",
                 "sportType": "FOOTBALL", "category": "U15",
                 "createdByStaffId": 5,
                 "joueurUserIds": [101, 102]}""".formatted(titre);
    }

    /** Body SANS joueurUserIds — V1 la liste est @NotEmpty, donc 400. */
    private static String bodySansJoueurs(String titre) {
        return """
                {"title": "%s", "description": "Seance technique",
                 "location": "Stade Mohammed V",
                 "sessionDate": "2026-09-01T18:00:00",
                 "sportType": "FOOTBALL", "category": "U15",
                 "createdByStaffId": 5}""".formatted(titre);
    }

    @Test
    void adherentNePeutPasCreerDeSeance() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "201")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Séance pirate")))
                .andExpect(status().isForbidden());
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void joueurNePeutPasCreerDeSeance() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "101")
                        .header("X-User-Email", "joueur@wydad.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Séance joueur")))
                .andExpect(status().isForbidden());
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void staffPeutCreerUneSeanceEtLeGroupeEstNotifie() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "301")
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Entraînement technique U15")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Entraînement technique U15"))
                .andExpect(jsonPath("$.category").value("U15"));

        // Phase 5 — ownership : createdByStaffId est l'ID de la fiche staff
        // résolue depuis le contexte JWT (jamais la valeur du payload client).
        Long ficheStaffId = staffRepository.findByUserId(301L).orElseThrow().getId();
        Session saved = sessionRepository.findAll().get(0);
        assertThat(saved.getCreatedByStaffId()).isEqualTo(ficheStaffId);
        assertThat(saved.getSportType())
                .isEqualTo(com.wydad.digital.sports.enums.SportType.FOOTBALL);

        // Chaque joueur du groupe visé (2 joueurs U15) est notifié
        Mockito.verify(notificationClient).notifyUser(eq(101L), Mockito.any(),
                anyString(), contains("Entraînement technique"), anyString());
        Mockito.verify(notificationClient).notifyUser(eq(102L), Mockito.any(),
                anyString(), contains("Entraînement technique"), anyString());

        // Le joueur U17 (autre catégorie) ne l'est PAS
        Mockito.verify(notificationClient, Mockito.never())
                .notifyUser(eq(103L), Mockito.any(), anyString(), anyString(), anyString());
    }

    @Test
    void adminPeutCreerUneSeance() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Stage physique ADMIN")))
                .andExpect(status().isCreated());
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void payloadInvalideRenvoie400EtRienNEstPersiste() throws Exception {
        // Titre manquant (avec joueurs OK)
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "301")
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest());

        // Date manquante (avec joueurs OK) — @NotNull sur sessionDate
        String sansDate = "{\"title\": \"Sans date\", \"sportType\": \"FOOTBALL\","
                + "\"category\": \"U15\", \"createdByStaffId\": 5,"
                + "\"joueurUserIds\": [101, 102]}";
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "301")
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sansDate))
                .andExpect(status().isBadRequest());

        // Liste joueurs vide — @NotEmpty sur joueurUserIds (V1)
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "301")
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodySansJoueurs("Sans joueurs")))
                .andExpect(status().isBadRequest());

        assertThat(sessionRepository.count()).isZero();
    }
}
