package com.wydad.digital.election;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * B.18 — Condition d'éligibilité au vote : un électeur doit avoir un
 * abonnement saisonnier ACTIF (soutien effectif au club). Le contrôle
 * est délégué à {@link AuthSubscriptionClient} (appel service-à-service
 * vers auth-service).
 *
 * <p>Le test unitaire mocke le client pour isoler la logique métier
 * d'ElectionService du réseau Docker.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:elecvotetest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ElectionMembershipRequiredTest {

    @Autowired
    private ElectionService electionService;

    @MockBean
    private AuthSubscriptionClient authSubscriptionClient;

    private Long electionId;
    private Long candidateId;

    @BeforeEach
    void seedElection() {
        UserContext.clear();
        UserContext.setCurrentUserEmail("admin@wac.ma");
        UserContext.setCurrentUserRole("ADMIN");
        CreateElectionRequest req = CreateElectionRequest.builder()
                .title("Élection test B.18")
                .startsAt(LocalDateTime.now().minusHours(1))
                .endsAt(LocalDateTime.now().plusHours(1))
                .candidates(List.of(
                        // B.8 — userId=null (rétro-compat : candidats
                        // « externes » saisis en texte libre, jamais liés
                        // à un titulaire actif). Acceptés par le service.
                        new AddCandidateRequest("Candidat A", null, null, 0, null),
                        new AddCandidateRequest("Candidat B", null, null, 1, null)
                ))
                .build();
        ElectionView created = electionService.create(req, "admin@wac.ma");
        electionId = created.getId();
        candidateId = created.getCandidates().get(0).getId();
        UserContext.clear();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("B.18 — vote refusé pour un électeur sans abonnement ACTIF")
    void voteRefuseSansAbonnement() {
        // Mock : auth-service répond false (pas d'abonnement).
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(false);

        UserContext.setCurrentUserId(500L);
        UserContext.setCurrentUserEmail("fan-sans-abonnement@wydad.ma");
        UserContext.setCurrentUserRole("ADHERENT");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> electionService.vote(electionId, candidateId));
        assertEquals("VOTE_REQUIRES_MEMBERSHIP", ex.getMessage(),
                "Le message d'erreur doit matcher exactement le code "
                        + "intercepté par GlobalExceptionHandler (403).");
    }

    @Test
    @DisplayName("B.18 — vote autorisé pour un adhérent avec abonnement ACTIF")
    void voteAutoriseAvecAbonnement() {
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(true);

        UserContext.setCurrentUserId(600L);
        UserContext.setCurrentUserEmail("adherent-abonne@wydad.ma");
        UserContext.setCurrentUserRole("ADHERENT");

        ElectionView apres = electionService.vote(electionId, candidateId);
        assertTrue(apres.getMyVoteIndex() != null,
                "Le vote doit être enregistré : myVoteIndex doit être positionné");
    }

    @Test
    @DisplayName("B.18 — ADMIN peut voter même sans abonnement (override)")
    void adminVoteSansAbonnement() {
        // Mock : false, mais ADMIN est exempté dans la logique.
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(false);

        UserContext.setCurrentUserId(1L);
        UserContext.setCurrentUserEmail("admin@wac.ma");
        UserContext.setCurrentUserRole("ADMIN");

        ElectionView apres = electionService.vote(electionId, candidateId);
        assertTrue(apres.getMyVoteIndex() != null,
                "ADMIN doit pouvoir voter même sans abonnement (override)");
    }
}
