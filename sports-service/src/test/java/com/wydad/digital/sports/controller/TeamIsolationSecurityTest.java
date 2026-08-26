package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolation discipline + catégorie sur les listings (cahier des charges §6/§24).
 *
 * La chaîne complète est couverte : en-têtes X-User-* (comme la gateway) ->
 * UserContextFilter -> SecurityContext -> @PreAuthorize -> TeamIsolationService.
 *
 * 1. un entraîneur Football U17 voit les joueurs Football U17 (200, liste exacte) ;
 * 2. le même entraîneur demandant Basketball U17 → 403 ;
 * 3. le même entraîneur demandant Football SENIOR → 403 ;
 * 4. un joueur ne peut pas lister l'effectif complet GET /players → 403 ;
 * 5. un joueur de son propre groupe → 200 ;
 * 6. un VISITEUR (rôle non prévu) sur /filter → 403 ;
 * 7. PRESIDENT : vision globale — n'importe quel groupe → 200.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teamiso;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.notification-service-uri=http://localhost:1"
})
@AutoConfigureMockMvc
class TeamIsolationSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired StaffRepository staffRepository;

    @MockBean com.wydad.digital.sports.client.NotificationClient notificationClient;

    private static final String EMAIL = "x-test@wydad.ma";

    @BeforeEach
    void seed() {
        // Effectif multi-groupes
        for (long uid = 10; uid < 13; uid++) {
            playerRepository.save(Player.builder()
                    .userId(uid).fullName("Joueur FB U17 #" + uid)
                    .sportType(SportType.FOOTBALL).category(Category.U17)
                    .position("Milieu").jerseyNumber((int) uid)
                    .build());
        }
        playerRepository.save(Player.builder()
                .userId(20L).fullName("Joueur BB U17")
                .sportType(SportType.BASKETBALL).category(Category.U17)
                .position("Ailier").jerseyNumber(4).build());
        playerRepository.save(Player.builder()
                .userId(21L).fullName("Joueur FB Senior")
                .sportType(SportType.FOOTBALL).category(Category.SENIOR)
                .position("Défenseur").jerseyNumber(5).build());

        // Coach Football U17 (le protagoniste des scénarios §25)
        staffRepository.save(Staff.builder()
                .userId(100L).fullName("Coach Foot U17")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.U17)
                .build());
    }

    @AfterEach
    void clean() {
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    @Test
    void entraineurVoitSonGroupe() throws Exception {
        mvc.perform(get("/api/sports/players/filter")
                        .param("sportType", "FOOTBALL").param("category", "U17")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "ENTRAINEUR")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void entraineurHorsGroupeDisciplineRefuse() throws Exception {
        mvc.perform(get("/api/sports/players/filter")
                        .param("sportType", "BASKETBALL").param("category", "U17")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "ENTRAINEUR")
                        .header("X-User-Id", "100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void entraineurHorsGroupeCategorieRefuse() throws Exception {
        mvc.perform(get("/api/sports/players/filter")
                        .param("sportType", "FOOTBALL").param("category", "SENIOR")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "ENTRAINEUR")
                        .header("X-User-Id", "100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void joueurNePeutPasListerToutLeffectif() throws Exception {
        mvc.perform(get("/api/sports/players")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void joueurVoitSonPropreGroupe() throws Exception {
        mvc.perform(get("/api/sports/players/filter")
                        .param("sportType", "FOOTBALL").param("category", "U17")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void visiteurRefuseSurFilter() throws Exception {
        mvc.perform(get("/api/sports/players/filter")
                        .param("sportType", "FOOTBALL").param("category", "U17")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "VISITEUR")
                        .header("X-User-Id", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void presidentVisionGlobale() throws Exception {
        mvc.perform(get("/api/sports/players/filter")
                        .param("sportType", "BASKETBALL").param("category", "U17")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "PRESIDENT")
                        .header("X-User-Id", "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
