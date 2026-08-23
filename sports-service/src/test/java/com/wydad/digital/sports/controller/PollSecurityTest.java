package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.PollDtos.CreatePollRequest;
import com.wydad.digital.sports.dto.PollDtos.PollResponse;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.service.PollService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.2 — Sondages : regles de securite et metier prouvees cote serveur.
 *
 * 1. seul l'ADMIN peut creer un sondage (403 pour ADHERENT) ;
 * 2. un membre vote, son userId vient du contexte JWT (jamais du body) ;
 * 3. le double vote est rejete (code + contrainte d'unicite en base) ;
 * 4. deux utilisateurs votent pour des options differentes : les resultats
 *    agrégés sont exacts ;
 * 5. un sondage clos refuse tout nouveau vote.
 *
 * H2 MODE=PostgreSQL (contrainte d'unicite SQL) ; a revalider aussi contre
 * le PostgreSQL reel (docker-compose).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:polltest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class PollSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PollService pollService;

    private Long pollId;

    @BeforeEach
    void seedPoll() {
        SportsUserContext.clear();
        PollResponse poll = pollService.createPoll(
                new CreatePollRequest("Meilleur joueur du mois ?",
                        List.of("Zerrouki", "Bakkali", "El Amrani"), null),
                "admin@wac.ma");
        pollId = poll.id();
    }

    @AfterEach
    void clearContext() {
        SportsUserContext.clear();
    }

    /** Seul l'ADMIN peut creer un sondage — prouve au niveau HTTP. */
    @Test
    void creationReserveeALAdmin() throws Exception {
        String body = """
                {"question":"Test ?","options":["Oui","Non"]}
                """;
        // Un ADHERENT est rejete 403 AVANT d'atteindre le service.
        mockMvc.perform(post("/api/sports/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user("membre@wydad.ma").roles("ADHERENT")))
                .andExpect(status().isForbidden());
        // L'ADMIN passe.
        mockMvc.perform(post("/api/sports/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user("admin@wac.ma").roles("ADMIN")))
                .andExpect(status().isCreated());
    }

    /** L'identite du votant = contexte JWT ; le double vote est rejete. */
    @Test
    void unMembreVoteUneSeuleFois() {
        SportsUserContext.setCurrentUserId(101L);
        SportsUserContext.setCurrentUserEmail("fan1@wydad.ma");
        SportsUserContext.setCurrentUserRole("ADHERENT");

        PollResponse apresPremierVote = pollService.vote(pollId, 0);
        assertEquals(1L, apresPremierVote.totalVotes());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> pollService.vote(pollId, 1));
        assertTrue(ex.getMessage().contains("déjà voté"),
                "Le second vote du MEME utilisateur doit etre rejete");

        // Le premier choix n'a pas ete ecrase par la tentative de re-vote.
        assertEquals(0L, pollService.getActivePolls().get(0).resultsPerOption().get(1));
    }

    /**
     * Deux membres, votes differents : total = 2 et repartition exacte.
     * Preuve que les resultats sont calcules serveur depuis les votes reels.
     */
    @Test
    void resultatsAgregesExactement() {
        SportsUserContext.setCurrentUserId(201L);
        SportsUserContext.setCurrentUserRole("ADHERENT");
        pollService.vote(pollId, 0);

        SportsUserContext.setCurrentUserId(202L);
        SportsUserContext.setCurrentUserRole("PARENT");
        pollService.vote(pollId, 2);

        List<Long> results = pollService.getActivePolls().get(0).resultsPerOption();
        assertEquals(List.of(1L, 0L, 1L), results);
        assertEquals(2L, pollService.getActivePolls().get(0).totalVotes());
    }

    /** Un sondage clos refuse tout nouveau vote. */
    @Test
    void sondageClosRefuseLeVote() {
        pollService.closePoll(pollId);

        SportsUserContext.setCurrentUserId(301L);
        SportsUserContext.setCurrentUserRole("ADHERENT");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> pollService.vote(pollId, 0));
        assertTrue(ex.getMessage().contains("clôturé"));
    }
}
