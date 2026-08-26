package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.client.ContentClient;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.MatchConvocation;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.MatchConvocationRepository;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Convocations de match (§8/§9) : sécurité et workflow complet.
 *
 * <p>Scénarios couverts :</p>
 * <ul>
 *   <li>l'entraîneur Foot U17 convoque ses propres joueurs pour un match
 *       réel Foot U17 (201, DRAFT) ;</li>
 *   <li>l'entraîneur Basket U17 ne peut PAS gérer la feuille Foot U17
 *       (403 — isolation discipline+catégorie §24/§26) ;</li>
 *   <li>un match inexistant est refusé en 400 (« pas de convocation sur un
 *       match fantôme », §17) ;</li>
 *   <li>un joueur ne peut ni convoquer ni soumettre (403) ;</li>
 *   <li>l'ADMIN voit les feuilles soumises, publie (PUBLIEE) ;</li>
 *   <li>la vue publique n'expose QUE le publié, y compris pour un appel
 *       anonyme sans aucun en-tête.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:convotest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.internal-secret=secret-de-test-interne"
})
@AutoConfigureMockMvc
class MatchConvocationSecurityTest {

    private static final String SECRET = "secret-de-test-interne";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentClient contentClient;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private MatchConvocationRepository convocationRepository;

    // userIds gateway-style : 101 = coach Foot U17, 102 = coach Basket U17,
    // 103 = joueur Foot U17, 104 = joueur Foot U17 #2, 105 = ADMIN.
    private static final long COACH_FOOT_U17 = 101L;
    private static final long COACH_BASKET_U17 = 102L;
    private static final long JOUEUR_1 = 103L;
    private static final long JOUEUR_2 = 104L;

    @BeforeEach
    void seed() {
        convocationRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();

        playerRepository.save(Player.builder()
                .userId(JOUEUR_1).fullName("Yassine Bounou")
                .sportType(SportType.FOOTBALL).category(Category.U17)
                .jerseyNumber(1).build());
        playerRepository.save(Player.builder()
                .userId(JOUEUR_2).fullName("Nouhaila Benzina")
                .sportType(SportType.FOOTBALL).category(Category.U17)
                .jerseyNumber(5).build());

        staffRepository.save(Staff.builder()
                .userId(COACH_FOOT_U17).fullName("Coach Foot")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.U17).build());
        staffRepository.save(Staff.builder()
                .userId(COACH_BASKET_U17).fullName("Coach Basket")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.BASKETBALL).assignedCategory(Category.U17).build());
    }

    /** Fiche match réelle Foot U17 côté content-service. */
    private void matchFootU17(long matchId) {
        when(contentClient.fetchMatch(matchId)).thenReturn(new ContentClient.MatchInfo(
                matchId, "Raja U17", "FOOTBALL", "U17", "Complexe Mohammed V"));
    }

    /** Match d'une autre discipline/catégorie (isolation §24). */
    private void matchFootSenior(long matchId) {
        when(contentClient.fetchMatch(matchId)).thenReturn(new ContentClient.MatchInfo(
                matchId, "Raja Senior", "FOOTBALL", "SENIOR", "Complexe Mohammed V"));
    }

    private String batchBody(long joueurUserId, String role) {
        return """
                {"players":[{"joueurUserId":%d,"playerRole":"%s"}]}
                """.formatted(joueurUserId, role);
    }

    @Test
    void entraineurConvoqueSonGroupePourUnMatchReel() throws Exception {
        matchFootU17(10L);
        mockMvc.perform(post("/api/sports/match-convocations/match/10")
                        .header("X-User-Id", COACH_FOOT_U17).header("X-User-Email", "coach-foot@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR")
                        .contentType(APPLICATION_JSON)
                        .content(batchBody(JOUEUR_1, "TITULAIRE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.rejected.length()").value(0))
                .andExpect(jsonPath("$.convocations[0].playerRole").value("TITULAIRE"))
                .andExpect(jsonPath("$.convocations[0].category").value("U17"));
    }

    @Test
    void entraineurAutreGroupeEstRefuse403() throws Exception {
        // Le coach Basket U17 cible un match FOOTBALL U17 : 403.
        matchFootU17(11L);
        mockMvc.perform(post("/api/sports/match-convocations/match/11")
                        .header("X-User-Id", COACH_BASKET_U17).header("X-User-Email", "coach-basket@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR")
                        .contentType(APPLICATION_JSON)
                        .content(batchBody(JOUEUR_1, "TITULAIRE")))
                .andExpect(status().isForbidden());

        // Et il cible un match Football SENIOR : 403 aussi (même discipline,
        // autre catégorie — l'isolation porte sur discipline+catégorie).
        matchFootSenior(12L);
        mockMvc.perform(post("/api/sports/match-convocations/match/12")
                        .header("X-User-Id", COACH_FOOT_U17).header("X-User-Email", "coach-foot@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR")
                    .contentType(APPLICATION_JSON)
                        .content(batchBody(JOUEUR_1, "TITULAIRE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void matchInexistantEstRefuse400() throws Exception {
        when(contentClient.fetchMatch(999L)).thenReturn(null);
        mockMvc.perform(post("/api/sports/match-convocations/match/999")
                        .header("X-User-Id", COACH_FOOT_U17).header("X-User-Email", "coach-foot@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR")
                        .contentType(APPLICATION_JSON)
                        .content(batchBody(JOUEUR_1, "TITULAIRE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joueurNePeutNiConvoquerNiSoumettre() throws Exception {
        matchFootU17(13L);
        mockMvc.perform(post("/api/sports/match-convocations/match/13")
                        .header("X-User-Id", JOUEUR_1).header("X-User-Email", "joueur1@wac.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType(APPLICATION_JSON)
                        .content(batchBody(JOUEUR_2, "REMPLACANT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/sports/match-convocations/match/13/submit")
                        .header("X-User-Id", JOUEUR_1).header("X-User-Email", "joueur1@wac.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void workflowCompletJusquAPublicationEtVuePublique() throws Exception {
        matchFootU17(14L);

        // 1. L'entraîneur convoque ses deux joueurs.
        mockMvc.perform(post("/api/sports/match-convocations/match/14")
                        .header("X-User-Id", COACH_FOOT_U17).header("X-User-Email", "coach-foot@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"players":[
                                  {"joueurUserId":103,"playerRole":"TITULAIRE"},
                                  {"joueurUserId":104,"playerRole":"REMPLACANT"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2));

        // 2. Il soumet à l'ADMIN → SOUMISE.
        mockMvc.perform(post("/api/sports/match-convocations/match/14/submit")
                        .header("X-User-Id", COACH_FOOT_U17).header("X-User-Email", "coach-foot@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SOUMISE"));

        // 3. Avant publication, la vue publique renvoie 404.
        mockMvc.perform(get("/api/sports/match-convocations/public/match/14"))
                .andExpect(status().isNotFound());

        // 4. Un entraîneur ne peut pas publier lui-même (403).
        mockMvc.perform(post("/api/sports/match-convocations/admin/match/14/publish")
                        .header("X-User-Id", COACH_FOOT_U17).header("X-User-Email", "coach-foot@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR"))
                .andExpect(status().isForbidden());

        // 5. L'ADMIN voit la feuille soumise puis publie.
        mockMvc.perform(get("/api/sports/match-convocations/admin/submitted")
                        .header("X-User-Id", 105L).header("X-User-Email", "admin@wac.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/sports/match-convocations/admin/match/14/publish")
                        .header("X-User-Id", 105L).header("X-User-Email", "admin@wac.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PUBLIEE"));

        // 6. Vue publique ANONYME (aucun en-tête) : titulaires/remplaçants.
        mockMvc.perform(get("/api/sports/match-convocations/public/match/14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulaires[0].fullName").value("Yassine Bounou"))
                .andExpect(jsonPath("$.remplacants[0].fullName").value("Nouhaila Benzina"))
                .andExpect(jsonPath("$.category").value("U17"));
    }

    @Test
    void joueurVoitSesPropresConvocationsUniquement() throws Exception {
        matchFootU17(15L);
        mockMvc.perform(post("/api/sports/match-convocations/match/15")
                        .header("X-User-Id", COACH_FOOT_U17).header("X-User-Email", "coach-foot@wac.ma")
                        .header("X-User-Role", "ENTRAINEUR")
                        .contentType(APPLICATION_JSON)
                        .content(batchBody(JOUEUR_1, "TITULAIRE")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/sports/match-convocations/my")
                        .header("X-User-Id", JOUEUR_1).header("X-User-Email", "joueur1@wac.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // L'autre joueur du groupe ne voit rien de la feuille de son camarade.
        mockMvc.perform(get("/api/sports/match-convocations/my")
                        .header("X-User-Id", JOUEUR_2).header("X-User-Email", "joueur2@wac.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
