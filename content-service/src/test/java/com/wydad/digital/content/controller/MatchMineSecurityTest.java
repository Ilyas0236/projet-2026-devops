package com.wydad.digital.content.controller;

import com.wydad.digital.content.client.SportsRosterClient;
import com.wydad.digital.content.model.Match;
import com.wydad.digital.content.model.MatchCategory;
import com.wydad.digital.content.model.MatchStatut;
import com.wydad.digital.content.model.SportSection;
import com.wydad.digital.content.repository.MatchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §16/§26 — « matchs de mon groupe » : la discipline et la catégorie sont
 * résolues SERVEUR-SIDE depuis la fiche roster (joueur/staff), jamais
 * depuis des paramètres falsifiables du client.
 *
 * 1. un joueur Football U17 ne voit que les matchs Football U17 ;
 * 2. un visiteur (sans en-têtes) obtient une liste vide — pas d'erreur ;
 * 3. un utilisateur authentifié sans fiche joueur/staff : liste vide ;
 * 4. ADMIN voit tous les matchs ;
 * 5. PRESIDENT voit tous les matchs (vision globale).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content_matchmine;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Transactional
class MatchMineSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired MatchRepository matchRepository;

    /** Le client roster est simulé : le groupe vient de sports-service. */
    @MockBean SportsRosterClient rosterClient;

    private void seedMatches() {
        matchRepository.save(Match.builder()
                .date(LocalDate.of(2026, 9, 5)).heure(LocalTime.of(20, 0))
                .adversaire("Raja FB U17").competition("Championnat U17").lieu("Stade Mohammed V")
                .statut(MatchStatut.PROGRAMME).sport(SportSection.FOOTBALL).categorie(MatchCategory.U17)
                .build());
        matchRepository.save(Match.builder()
                .date(LocalDate.of(2026, 9, 6)).heure(LocalTime.of(18, 30))
                .adversaire("FUS Senior").competition("Botola").lieu("Stade Mohammed V")
                .statut(MatchStatut.PROGRAMME).sport(SportSection.FOOTBALL).categorie(MatchCategory.SENIOR)
                .build());
        matchRepository.save(Match.builder()
                .date(LocalDate.of(2026, 9, 7)).heure(LocalTime.of(19, 0))
                .adversaire("ASB Basket U17").competition("Championnat Basket U17").lieu("Salle Anassi")
                .statut(MatchStatut.PROGRAMME).sport(SportSection.BASKETBALL).categorie(MatchCategory.U17)
                .build());
    }

    @AfterEach
    void clean() {
        matchRepository.deleteAll();
    }

    private void stubMembership(Long userId, String sportType, String category) {
        when(rosterClient.fetchMembership(userId)).thenReturn(
                new SportsRosterClient.Membership(sportType, category, "JOUEUR", "Joueur Test"));
    }

    @Test
    void joueurFootU17NeVoitQueSonGroupe() throws Exception {
        seedMatches();
        stubMembership(10L, "FOOTBALL", "U17");

        mockMvc.perform(get("/api/content/matches/mine")
                        .header("X-User-Email", "joueur@wydad.ma")
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categorie").value("U17"))
                .andExpect(jsonPath("$[0].sport").value("FOOTBALL"));
    }

    @Test
    void joueurBasketU17NeVoitPasLesMatchsFootball() throws Exception {
        seedMatches();
        stubMembership(11L, "BASKETBALL", "U17");

        mockMvc.perform(get("/api/content/matches/mine")
                        .header("X-User-Email", "basketteur@wydad.ma")
                        .header("X-User-Role", "JOUEUR")
                        .header("X-User-Id", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sport").value("BASKETBALL"));
    }

    @Test
    void visiteurSansEnTetesListeVide() throws Exception {
        seedMatches();
        mockMvc.perform(get("/api/content/matches/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void utilisateurSansFicheRosterListeVide() throws Exception {
        seedMatches();
        when(rosterClient.fetchMembership(50L)).thenReturn(null);

        mockMvc.perform(get("/api/content/matches/mine")
                        .header("X-User-Email", "fan@wydad.ma")
                        .header("X-User-Role", "ADHERENT")
                        .header("X-User-Id", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminVoitTousLesMatchs() throws Exception {
        seedMatches();
        mockMvc.perform(get("/api/content/matches/mine")
                        .header("X-User-Email", "admin@wac.ma")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void presidentVisionGlobale() throws Exception {
        seedMatches();
        mockMvc.perform(get("/api/content/matches/mine")
                        .header("X-User-Email", "president@wac.ma")
                        .header("X-User-Role", "PRESIDENT")
                        .header("X-User-Id", "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
