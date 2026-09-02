package com.wydad.digital.election;

import com.wydad.digital.election.client.ActiveMembersClient;
import com.wydad.digital.election.client.AuthSubscriptionClient;
import com.wydad.digital.election.dto.ElectionDtos.AddCandidateRequest;
import com.wydad.digital.election.dto.ElectionDtos.CreateElectionRequest;
import com.wydad.digital.election.dto.ElectionDtos.ElectionView;
import com.wydad.digital.election.filter.UserContext;
import com.wydad.digital.election.service.ElectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * B.8.h — Le champ {@code canVote} exposé à la page publique doit
 * refléter l'éligibilité réelle de l'électeur, pas juste « utilisateur
 * authentifié, dans la fenêtre, n'a pas voté ».
 *
 * <p>Le propriétaire a fixé la règle : « voter = être titulaire d'une
 * carte d'abonnement ACTIVE au moment du vote ». Donc si un non-titulaire
 * (ou un titulaire dont la carte a expiré) ouvre la page /elections, on
 * doit lui afficher « canVote=false » pour qu'il ne clique pas sur un
 * bouton qui le mènera à un 403 VOTE_REQUIRES_MEMBERSHIP.</p>
 *
 * <p>La règle « achat pendant le scrutin = peut voter » est respectée :
 * le check est fait à chaque appel de {@code toView()}, pas figé à
 * {@code startsAt}.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eleceligiletest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ElectionCanVoteEligibilityTest {

    @Autowired private ElectionService electionService;
    @MockBean private AuthSubscriptionClient authSubscriptionClient;
    @MockBean private ActiveMembersClient activeMembersClient;

    private Long electionId;

    @BeforeEach
    void seedElection() {
        when(activeMembersClient.countActiveAt(any())).thenReturn(3L);

        UserContext.setCurrentUserEmail("admin@wac.ma");
        UserContext.setCurrentUserRole("ADMIN");
        UserContext.setCurrentUserId(1L);
        CreateElectionRequest req = CreateElectionRequest.builder()
                .title("Test B.8.h canVote")
                .startsAt(LocalDateTime.now().minusHours(1))
                .endsAt(LocalDateTime.now().plusHours(1))
                .candidates(List.of(
                        new AddCandidateRequest("Candidat A", null, null, 0, null),
                        new AddCandidateRequest("Candidat B", null, null, 1, null)
                ))
                .build();
        electionId = electionService.create(req, "admin@wac.ma").getId();
        UserContext.clear();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("B.8.h — non-titulaire : canVote=false (même si dans la fenêtre)")
    void nonTitulaire_canVoteFalse() {
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(false);

        UserContext.setCurrentUserId(500L);
        UserContext.setCurrentUserEmail("fan@wydad.ma");
        UserContext.setCurrentUserRole("ADHERENT");

        ElectionView v = electionService.getOpenForCurrentUser().get(0);
        assertFalse(v.isCanVote(),
                "Un non-titulaire doit voir canVote=false (sinon il clique et se prend 403)");
    }

    @Test
    @DisplayName("B.8.h — titulaire actif : canVote=true")
    void titulaireActif_canVoteTrue() {
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(true);

        UserContext.setCurrentUserId(600L);
        UserContext.setCurrentUserEmail("adherent@wydad.ma");
        UserContext.setCurrentUserRole("ADHERENT");

        ElectionView v = electionService.getOpenForCurrentUser().get(0);
        assertTrue(v.isCanVote(),
                "Un titulaire avec carte ACTIVE doit voir canVote=true");
    }

    @Test
    @DisplayName("B.8.h — achat pendant le scrutin : peut voter (règle non figée)")
    void achatPendantScrutin_peutVoter() {
        // Scénario utilisateur : "J'achète ma carte 5 min après le lancement
        // du scrutin, est-ce que je peux voter ?"
        // Réponse attendue : OUI, parce que le check titulaire se fait à
        // l'instant T (chaque appel toView), pas figé à startsAt.
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(true);

        UserContext.setCurrentUserId(700L);
        UserContext.setCurrentUserEmail("nouveau-titulaire@wydad.ma");
        UserContext.setCurrentUserRole("ADHERENT");

        ElectionView v = electionService.getOpenForCurrentUser().get(0);
        assertTrue(v.isCanVote(),
                "Un user qui vient d'acheter sa carte pendant le scrutin doit pouvoir voter");
    }

    @Test
    @DisplayName("B.8.h — ADMIN : canVote=true même sans abonnement (override)")
    void admin_canVoteTrue_sansAbonnement() {
        // L'admin doit pouvoir tester le flow de bout en bout même si
        // auth-service est momentanément down (cf. commentaire dans vote()).
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(false);

        UserContext.setCurrentUserId(1L);
        UserContext.setCurrentUserEmail("admin@wac.ma");
        UserContext.setCurrentUserRole("ADMIN");

        ElectionView v = electionService.getOpenForCurrentUser().get(0);
        assertTrue(v.isCanVote(), "ADMIN doit toujours voir canVote=true");
    }

    @Test
    @DisplayName("B.8.h — visiteur anonyme (pas de userId) : canVote=false")
    void visiteurAnonyme_canVoteFalse() {
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(true);

        // Pas de UserContext.setCurrentUserId() — visiteur non connecté
        ElectionView v = electionService.getOpenForCurrentUser().get(0);
        assertFalse(v.isCanVote(),
                "Un visiteur non connecté ne peut pas voter (pas même de UserContext)");
    }
}
