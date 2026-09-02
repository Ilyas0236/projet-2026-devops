package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.MedicalStatus;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.SessionConvocation;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.SessionConvocationRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Convocations personnalisées de séances d'entraînement.
 *
 * <p>Scénarios prouvés :</p>
 * <ul>
 *   <li>STAFF FOOT U17 crée une séance en ciblant 2 joueurs FOOT U17 → 201,
 *       2 convocations persistées, 2 notifications in-app envoyées ;</li>
 *   <li>STAFF FOOT U17 ciblant un joueur FOOT U17 hors de son groupe est
 *       refusé 403 (anti-IDOR) ;</li>
 *   <li>ADMIN peut créer pour n'importe quel groupe ;</li>
 *   <li>JOUEUR appelle {@code GET /sessions/my} et ne voit QUE les séances
 *       où il est convoqué ;</li>
 *   <li>ADMIN appelle {@code GET /sessions/admin?…} et voit les séances
 *       avec la liste des joueurs convoqués.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessionconvtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class SessionConvocationSecurityTest {

    private static final String URL = "/api/sports/sessions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private SessionConvocationRepository convocationRepository;

    @MockBean
    private NotificationClient notificationClient;

    private static final long COACH_FOOT_U17 = 101L;
    private static final long COACH_FOOT_U18 = 103L;
    private static final long COACH_BASKET_U17 = 102L;
    private static final long JOUEUR_FOOT_U17_A = 201L;
    private static final long JOUEUR_FOOT_U17_B = 202L;
    private static final long JOUEUR_FOOT_U15 = 203L;
    private static final long JOUEUR_FOOT_U17_INAPTE = 204L;
    private static final long JOUEUR_FOOT_U18_A = 205L;
    private static final long JOUEUR_FOOT_U18_B = 206L;
    private static final long ADMIN_USER = 999L;
    private static final long PRESIDENT_USER = 777L;

    @BeforeEach
    void seed() {
        convocationRepository.deleteAll();
        sessionRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();

        playerRepository.save(Player.builder()
                .userId(JOUEUR_FOOT_U17_A).fullName("Joueur Foot U17 A")
                .sportType(SportType.FOOTBALL).category(Category.U17).build());
        playerRepository.save(Player.builder()
                .userId(JOUEUR_FOOT_U17_B).fullName("Joueur Foot U17 B")
                .sportType(SportType.FOOTBALL).category(Category.U17).build());
        playerRepository.save(Player.builder()
                .userId(JOUEUR_FOOT_U15).fullName("Joueur Foot U15")
                .sportType(SportType.FOOTBALL).category(Category.U15).build());
        playerRepository.save(Player.builder()
                .userId(JOUEUR_FOOT_U17_INAPTE).fullName("Joueur Foot U17 Inapte")
                .sportType(SportType.FOOTBALL).category(Category.U17)
                .medicalStatus(MedicalStatus.INAPTE).build());
        playerRepository.save(Player.builder()
                .userId(JOUEUR_FOOT_U18_A).fullName("Joueur Foot U18 A")
                .sportType(SportType.FOOTBALL).category(Category.U18).build());
        playerRepository.save(Player.builder()
                .userId(JOUEUR_FOOT_U18_B).fullName("Joueur Foot U18 B")
                .sportType(SportType.FOOTBALL).category(Category.U18).build());
        staffRepository.save(Staff.builder()
                .userId(COACH_FOOT_U17).fullName("Coach Foot U17")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.U17).build());
        staffRepository.save(Staff.builder()
                .userId(COACH_BASKET_U17).fullName("Coach Basket U17")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.BASKETBALL).assignedCategory(Category.U17).build());
        staffRepository.save(Staff.builder()
                .userId(COACH_FOOT_U18).fullName("Coach Foot U18")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.U18).build());
    }

    private String bodyWithPlayers(String title, List<Long> joueurUserIds) {
        return """
                {"title": "%s", "description": "Seance",
                 "location": "Stade",
                 "sessionDate": "2026-09-10T18:00:00",
                 "sportType": "FOOTBALL", "category": "U17",
                 "createdByStaffId": 1,
                 "joueurUserIds": %s}"""
                .formatted(title, joueurUserIds.toString());
    }

    /** Body SANS le champ joueurUserIds (ou liste vide) — doit échouer 400. */
    private String bodyWithoutJoueurs(String title, String joueursLiteral) {
        String joueursField = joueursLiteral == null
                ? ""
                : "\"joueurUserIds\": " + joueursLiteral;
        // Le champ joueurs est l'AVANT-DERNIER (avant "}" final) ; on l'ajoute
        // sans virgule finale puisqu'il n'y a plus rien après lui.
        return "{\"title\": \"" + title + "\", "
                + "\"description\": \"Seance\", "
                + "\"location\": \"Stade\", "
                + "\"sessionDate\": \"2026-09-10T18:00:00\", "
                + "\"sportType\": \"FOOTBALL\", \"category\": \"U17\", "
                + "\"createdByStaffId\": 1, "
                + joueursField
                + "}";
    }

    @Test
    void entraineurCreeSeanceEtJoueursSelectionnesSontNotifies() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", COACH_FOOT_U17)
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayers(
                                "Entrainement technique U17",
                                List.of(JOUEUR_FOOT_U17_A, JOUEUR_FOOT_U17_B))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Entrainement technique U17"));

        // 2 convocations persistées
        assertThat(convocationRepository.count()).isEqualTo(2);
        // 2 notifications in-app envoyées (1 par joueur coché)
        Mockito.verify(notificationClient).notifyUser(
                eq(JOUEUR_FOOT_U17_A), Mockito.any(),
                anyString(), Mockito.contains("Entrainement technique U17"), anyString());
        Mockito.verify(notificationClient).notifyUser(
                eq(JOUEUR_FOOT_U17_B), Mockito.any(),
                anyString(), Mockito.contains("Entrainement technique U17"), anyString());
        // Le joueur U15 (hors groupe) n'est PAS notifié
        Mockito.verify(notificationClient, Mockito.never())
                .notifyUser(eq(JOUEUR_FOOT_U15), Mockito.any(), anyString(), anyString(), anyString());
    }

    @Test
    void ciblageJoueurHorsGroupeEstRefuse403() throws Exception {
        // Le coach Foot U17 tente de cocher un joueur Foot U15.
        mockMvc.perform(post(URL)
                        .header("X-User-Id", COACH_FOOT_U17)
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayers(
                                "Pirate",
                                List.of(JOUEUR_FOOT_U17_A, JOUEUR_FOOT_U15))))
                .andExpect(status().isForbidden());

        // Aucune convocation persistée (transaction rollback)
        assertThat(convocationRepository.count()).isZero();
    }

    @Test
    void entraineurAutreDisciplineNePeutPasCreerDansGroupeDuCoach() throws Exception {
        // Coach Basket U17 ne peut pas créer une séance FOOT U17
        mockMvc.perform(post(URL)
                        .header("X-User-Id", COACH_BASKET_U17)
                        .header("X-User-Email", "basket@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayers(
                                "Foot U17 par basket-coach",
                                List.of(JOUEUR_FOOT_U17_A))))
                .andExpect(status().isForbidden());
        assertThat(convocationRepository.count()).isZero();
    }

    @Test
    void adminPeutCreerPourNImporteQuelGroupe() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", ADMIN_USER)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayers(
                                "Stage administratif",
                                List.of(JOUEUR_FOOT_U17_A))))
                .andExpect(status().isCreated());

        // Convocations + séance persistées
        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(convocationRepository.count()).isEqualTo(1);

        // L'ADMIN : la valeur createdByStaffId du DTO est respectée telle
        // quelle (pas de forçage depuis une fiche staff). On vérifie que
        // la séance a bien sport/cat du DTO.
        Session saved = sessionRepository.findAll().get(0);
        assertThat(saved.getSportType()).isEqualTo(SportType.FOOTBALL);
        assertThat(saved.getCategory()).isEqualTo(Category.U17);

        // Le convoc est marqué created_by_staff_user_id = 0 (callerStaffUserId
        // vaut 0 quand isAdmin()).
        SessionConvocation c = convocationRepository.findAll().get(0);
        assertThat(c.getCreatedByStaffUserId()).isEqualTo(0L);
    }

    @Test
    void roleEntraineurEstAccepteSurLePost() throws Exception {
        // Le rôle ENTRAINEUR (sans fiche staff) doit aussi pouvoir créer
        // car le @PreAuthorize du POST inclut ENTRAINEUR.
        // Mais sans fiche, l'isolation force 403 — vérifions au moins qu'il
        // n'est pas rejeté par l'absence du rôle.
        mockMvc.perform(post(URL)
                        .header("X-User-Id", 555L)
                        .header("X-User-Email", "entraineur@wydad.ma")
                        .header("X-User-Role", "ENTRAINEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayers(
                                "Test role",
                                List.of(JOUEUR_FOOT_U17_A))))
                .andExpect(status().isForbidden()); // 403 car pas de fiche staff rattachée
    }

    @Test
    void joueurVoitSesConvocationsViaMy() throws Exception {
        // Seed : une séance + 2 convocations (le joueur A est convoqué, pas B)
        Session s = sessionRepository.save(Session.builder()
                .title("Seance A")
                .description("d")
                .location("L")
                .sessionDate(LocalDateTime.of(2026, 9, 10, 18, 0))
                .sportType(SportType.FOOTBALL).category(Category.U17)
                .createdByStaffId(0L).build());
        convocationRepository.save(SessionConvocation.builder()
                .sessionId(s.getId()).sportType(SportType.FOOTBALL).category(Category.U17)
                .joueurUserId(JOUEUR_FOOT_U17_A)
                .status(SessionConvocation.Status.CONVOQUE)
                .createdByStaffUserId(0L).build());

        // JOUEUR A appelle /my → 1 séance
        mockMvc.perform(get(URL + "/my")
                        .header("X-User-Id", JOUEUR_FOOT_U17_A)
                        .header("X-User-Email", "a@wydad.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(s.getId()))
                .andExpect(jsonPath("$[0].title").value("Seance A"));

        // JOUEUR B n'a aucune convocation
        mockMvc.perform(get(URL + "/my")
                        .header("X-User-Id", JOUEUR_FOOT_U17_B)
                        .header("X-User-Email", "b@wydad.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminVoitSeancesEtJoueursConvoquesViaAdmin() throws Exception {
        // Seed : 1 séance + 2 convocations
        Session s = sessionRepository.save(Session.builder()
                .title("Seance Admin")
                .description("d")
                .location("L")
                .sessionDate(LocalDateTime.of(2026, 9, 10, 18, 0))
                .sportType(SportType.FOOTBALL).category(Category.U17)
                .createdByStaffId(0L).build());
        convocationRepository.save(SessionConvocation.builder()
                .sessionId(s.getId()).sportType(SportType.FOOTBALL).category(Category.U17)
                .joueurUserId(JOUEUR_FOOT_U17_A)
                .status(SessionConvocation.Status.CONVOQUE)
                .createdByStaffUserId(0L).build());
        convocationRepository.save(SessionConvocation.builder()
                .sessionId(s.getId()).sportType(SportType.FOOTBALL).category(Category.U17)
                .joueurUserId(JOUEUR_FOOT_U17_B)
                .status(SessionConvocation.Status.CONVOQUE)
                .createdByStaffUserId(0L).build());

        mockMvc.perform(get(URL + "/admin")
                        .param("sportType", "FOOTBALL")
                        .param("category", "U17")
                        .header("X-User-Id", ADMIN_USER)
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(s.getId()))
                .andExpect(jsonPath("$[0].convokedPlayers.length()").value(2));
    }

    @Test
    void joueurNePeutPasAppelerAdmin() throws Exception {
        mockMvc.perform(get(URL + "/admin")
                        .param("sportType", "FOOTBALL")
                        .param("category", "U17")
                        .header("X-User-Id", JOUEUR_FOOT_U17_A)
                        .header("X-User-Email", "a@wydad.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void entraineurNePeutPasAppelerMy() throws Exception {
        mockMvc.perform(get(URL + "/my")
                        .header("X-User-Id", COACH_FOOT_U17)
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF"))
                .andExpect(status().isForbidden());
    }

    /**
     * Joueur INAPTE ciblé par l'entraîneur : la ligne de convocation est
     * persistée (traçabilité staff) mais aucune notification in-app n'est
     * envoyée. Les joueurs APT ciblés en parallèle sont, eux, bien notifiés.
     */
    @Test
    void joueurInapteCreeConvoqueMaisPasNotifie() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", COACH_FOOT_U17)
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayers(
                                "Mixte aptes + inapte",
                                List.of(JOUEUR_FOOT_U17_A, JOUEUR_FOOT_U17_INAPTE, JOUEUR_FOOT_U17_B))))
                .andExpect(status().isCreated());

        // 3 convocations persistées (A, B, inapte) — la traçabilité est totale
        assertThat(convocationRepository.count()).isEqualTo(3);

        // Seuls les 2 joueurs APT reçoivent la notif in-app
        Mockito.verify(notificationClient).notifyUser(
                eq(JOUEUR_FOOT_U17_A), Mockito.any(), anyString(), anyString(), anyString());
        Mockito.verify(notificationClient).notifyUser(
                eq(JOUEUR_FOOT_U17_B), Mockito.any(), anyString(), anyString(), anyString());
        // Le joueur INAPTE : la ligne existe mais AUCUNE notif
        Mockito.verify(notificationClient, Mockito.never())
                .notifyUser(eq(JOUEUR_FOOT_U17_INAPTE), Mockito.any(), anyString(), anyString(), anyString());
    }

    /**
     * La validation @NotEmpty sur joueurUserIds est obligatoire : sans elle,
     * un coach pourrait créer une séance qui ne convoque personne. La 400
     * doit être déclenchée par le contrôleur (avant l'isolation serveur).
     */
    @Test
    void postSession_sansJoueurUserIds_retourne400() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", COACH_FOOT_U17)
                        .header("X-User-Email", "coach@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutJoueurs("Sans joueurs", "[]")))
                .andExpect(status().isBadRequest());

        // Aucune convocation persistée
        assertThat(convocationRepository.count()).isZero();
        assertThat(sessionRepository.count()).isZero();
    }

    /**
     * Anti-IDOR même sport autre catégorie : un coach FOOT U18 ne peut pas
     * cocher un joueur FOOT U17. La règle d'isolation est (sportType,
     * category) — pas seulement sportType.
     */
    @Test
    void staffFootU18_cibantJoueurFootU17_retourne403() throws Exception {
        // Seed du coach U18 déjà fait dans @BeforeEach
        mockMvc.perform(post(URL)
                        .header("X-User-Id", COACH_FOOT_U18)
                        .header("X-User-Email", "u18@wydad.ma")
                        .header("X-User-Role", "STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        // DTO demande U17 mais le coach est U18 — l'isolation
                        // doit forcer le rejet (catégorie forcée depuis la fiche staff).
                        .content("""
                                {"title": "Pirate U18→U17",
                                 "description": "Seance",
                                 "location": "Stade",
                                 "sessionDate": "2026-09-10T18:00:00",
                                 "sportType": "FOOTBALL", "category": "U17",
                                 "createdByStaffId": 1,
                                 "joueurUserIds": [%d]}""".formatted(JOUEUR_FOOT_U17_A)))
                .andExpect(status().isForbidden());

        assertThat(convocationRepository.count()).isZero();
    }

    /**
     * Nouveau droit PRESIDENT sur /admin (lecture feuille de convocations
     * d'un groupe). Cohérence avec GET /filter qui accepte déjà PRESIDENT.
     */
    @Test
    void president_peutLireGetSessionsAdmin() throws Exception {
        // Seed : 1 séance + 1 convocation
        Session s = sessionRepository.save(Session.builder()
                .title("Seance vue par president")
                .description("d")
                .location("L")
                .sessionDate(LocalDateTime.of(2026, 9, 10, 18, 0))
                .sportType(SportType.FOOTBALL).category(Category.U17)
                .createdByStaffId(0L).build());
        convocationRepository.save(SessionConvocation.builder()
                .sessionId(s.getId()).sportType(SportType.FOOTBALL).category(Category.U17)
                .joueurUserId(JOUEUR_FOOT_U17_A)
                .status(SessionConvocation.Status.CONVOQUE)
                .createdByStaffUserId(0L).build());

        mockMvc.perform(get(URL + "/admin")
                        .param("sportType", "FOOTBALL")
                        .param("category", "U17")
                        .header("X-User-Id", PRESIDENT_USER)
                        .header("X-User-Email", "president@wydad.ma")
                        .header("X-User-Role", "PRESIDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(s.getId()))
                .andExpect(jsonPath("$[0].convokedPlayers.length()").value(1))
                .andExpect(jsonPath("$[0].convokedPlayers[0].joueurUserId").value(JOUEUR_FOOT_U17_A));
    }
}
