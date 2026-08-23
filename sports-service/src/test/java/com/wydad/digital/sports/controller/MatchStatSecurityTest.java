package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.4 — Preuves serveur des statistiques de match réelles.
 *
 * 1. le staff d'une AUTRE catégorie ne peut pas saisir de stat (403) ;
 * 2. le staff de la catégorie saisit une stat → 201, totaux de la fiche
 *    agrégés (matchesPlayed/goals/assists recalculés depuis les lignes) ;
 * 3. l'ADMIN peut saisir pour n'importe quel joueur ;
 * 4. le joueur consulte SES stats détaillées uniquement.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:matchstat;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Note : H2 MODE=PostgreSQL embarqué pour la CI sans démon Docker ;
        // revalider sur PostgreSQL réel au déploiement.
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class MatchStatSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired MatchStatRepository matchStatRepository;

    private Player joueur(Long uid, String nom) {
        return playerRepository.save(Player.builder()
                .userId(uid).fullName(nom)
                .sportType(SportType.FOOTBALL).category(Category.U19)
                .position("Milieu").jerseyNumber(8)
                .build());
    }

    private void staff(Long uid, SportType sport, Category cat) {
        staffRepository.save(com.wydad.digital.sports.model.Staff.builder()
                .userId(uid).fullName("Coach")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(sport).assignedCategory(cat)
                .build());
    }

    private static final String EMAIL = "x-test@wydad.ma";

    @AfterEach
    void clean() {
        matchStatRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    @Test
    void staffAutreCategorieRefuse() throws Exception {
        joueur(501L, "Joueur Stats");
        staff(500L, SportType.HANDBALL, Category.PRO);

        mvc.perform(post("/api/sports/my-space/staff/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "500")
                        .param("joueurUserId", "501")
                        .contentType("application/json")
                        .content("{\"opponent\":\"Raja U19\",\"matchDate\":\"2026-03-01\",\"goals\":2,\"assists\":1}"))
                .andExpect(status().isForbidden());

        assertThat(matchStatRepository.findAll()).isEmpty();
    }

    @Test
    void staffCategorieSaisit_etTotauxAgreges() throws Exception {
        Player p = joueur(502L, "Joueur Stats 2");
        staff(503L, SportType.FOOTBALL, Category.U19);

        mvc.perform(post("/api/sports/my-space/staff/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "503")
                        .param("joueurUserId", "502")
                        .contentType("application/json")
                        .content("{\"opponent\":\"Raja U19\",\"matchDate\":\"2026-03-01\",\"goals\":2,\"assists\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.opponent").value("Raja U19"));

        // Deuxième match
        mvc.perform(post("/api/sports/my-space/staff/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "503")
                        .param("joueurUserId", "502")
                        .contentType("application/json")
                        .content("{\"opponent\":\"FUS U19\",\"matchDate\":\"2026-03-08\",\"goals\":0,\"assists\":2}"))
                .andExpect(status().isCreated());

        Player apres = playerRepository.findById(p.getId()).orElseThrow();
        assertThat(apres.getMatchesPlayed()).isEqualTo(2);   // agrégé, pas saisi à la main
        assertThat(apres.getGoals()).isEqualTo(2);
        assertThat(apres.getAssists()).isEqualTo(3);
    }

    @Test
    void adminPeutSaisirPourNImporteQuelJoueur() throws Exception {
        joueur(504L, "Joueur Stats 3");

        mvc.perform(post("/api/sports/my-space/staff/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "999")
                        .param("joueurUserId", "504")
                        .contentType("application/json")
                        .content("{\"opponent\":\"WAC vs FAR\",\"matchDate\":\"2026-04-02\",\"goals\":1}"))
                .andExpect(status().isCreated());

        assertThat(matchStatRepository.findByJoueurUserIdOrderByMatchDateDesc(504L)).hasSize(1);
    }

    @Test
    void adversaireObligatoire() throws Exception {
        joueur(505L, "Joueur Stats 4");
        staff(506L, SportType.FOOTBALL, Category.U19);

        mvc.perform(post("/api/sports/my-space/staff/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "506")
                        .param("joueurUserId", "505")
                        .contentType("application/json")
                        .content("{\"opponent\":\" \",\"matchDate\":\"2026-03-01\"}"))
                .andExpect(status().isBadRequest());

        assertThat(matchStatRepository.findAll()).isEmpty();
    }

    @Test
    void joueurNeVoitQueSesStats() throws Exception {
        joueur(507L, "Joueur Moi");
        Player autre = joueur(508L, "Joueur Autre");
        staff(509L, SportType.FOOTBALL, Category.U19);

        // Deux matchs pour 507, un pour 508
        for (String date : new String[]{"2026-03-01", "2026-03-08"}) {
            mvc.perform(post("/api/sports/my-space/staff/stats")
                            .header("X-User-Email", EMAIL)
                            .header("X-User-Role", "STAFF")
                            .header("X-User-Id", "509")
                            .param("joueurUserId", "507")
                            .contentType("application/json")
                            .content("{\"opponent\":\"Adv\",\"matchDate\":\"" + date + "\",\"goals\":1}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/sports/my-space/staff/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Id", "509")
                        .param("joueurUserId", "508")
                        .contentType("application/json")
                        .content("{\"opponent\":\"Adv B\",\"matchDate\":\"2026-03-02\"}"))
                .andExpect(status().isCreated());

        // Le joueur 507 ne voit que SES deux lignes
        mvc.perform(get("/api/sports/my-space/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "507"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].goals").value(1));

        // Un JOUEUR ne peut PAS lire les stats d'un autre via la route staff
        mvc.perform(get("/api/sports/my-space/staff/stats")
                        .header("X-User-Email", EMAIL)
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "507")
                        .param("joueurUserId", String.valueOf(autre.getUserId())))
                .andExpect(status().isForbidden());
    }
}
