package com.wydad.digital.election;

import com.wydad.digital.election.client.ActiveMembersClient;
import com.wydad.digital.election.client.AuthSubscriptionClient;
import com.wydad.digital.election.client.NotificationClient;
import com.wydad.digital.election.dto.ElectionDtos.AddCandidateRequest;
import com.wydad.digital.election.dto.ElectionDtos.CreateElectionRequest;
import com.wydad.digital.election.dto.ElectionDtos.ElectionView;
import com.wydad.digital.election.filter.UserContext;
import com.wydad.digital.election.model.ElectionStatus;
import com.wydad.digital.election.repository.ElectionRepository;
import com.wydad.digital.election.repository.ElectionVoteRepository;
import com.wydad.digital.election.service.ElectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * B.8.b — Mutations admin sur une élection : suppression, modification,
 * dépublication. Chaque test isole une règle métier refusée par le
 * service (vote > 0 interdit, status != OPEN interdit, etc.).
 *
 * <p>Test service direct (pas MockMvc) — plus rapide, focalisé sur la
 * logique métier.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:elecmuttest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ElectionAdminMutationsTest {

    @Autowired private ElectionService electionService;
    @Autowired private ElectionRepository electionRepository;
    @Autowired private ElectionVoteRepository voteRepository;

    @MockBean private ActiveMembersClient activeMembersClient;
    @MockBean private AuthSubscriptionClient authSubscriptionClient;
    @MockBean private NotificationClient notificationClient;

    private Long electionId;
    private static final String ADMIN_EMAIL = "admin@wac.ma";

    @BeforeEach
    void setUp() {
        when(activeMembersClient.countActiveAt(any())).thenReturn(3L);
        when(authSubscriptionClient.isActiveSubscriber(anyString())).thenReturn(true);

        UserContext.setCurrentUserEmail(ADMIN_EMAIL);
        UserContext.setCurrentUserRole("ADMIN");
        UserContext.setCurrentUserId(999L);

        // Élection de test avec 2 candidats — fenêtre large.
        List<AddCandidateRequest> candidats = new ArrayList<>();
        candidats.add(new AddCandidateRequest("Candidat A", null, null, null, 1L));
        candidats.add(new AddCandidateRequest("Candidat B", null, null, null, 2L));
        CreateElectionRequest req = CreateElectionRequest.builder()
                .title("Test mutation admin")
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(30))
                .candidates(candidats)
                .build();
        ElectionView v = electionService.create(req, ADMIN_EMAIL);
        electionId = v.getId();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("B.8.b — supprimer une élection 0 vote : OK")
    void supprimer_zeroVote_ok() {
        electionService.deleteForAdmin(electionId);
        assertTrue(electionRepository.findById(electionId).isEmpty(),
                "L'élection aurait dû être supprimée");
    }

    @Test
    @DisplayName("B.8.b — supprimer une élection avec votes : refusé")
    void supprimer_avecVotes_refuse() {
        ElectionView v0 = electionService.get(electionId);
        electionService.vote(electionId, v0.getCandidates().get(0).getId());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> electionService.deleteForAdmin(electionId));
        assertTrue(ex.getMessage().toLowerCase().contains("vote"),
                "Le message doit mentionner les votes, trouvé : " + ex.getMessage());
        assertTrue(electionRepository.findById(electionId).isPresent(),
                "L'élection aurait dû rester en BDD");
    }

    @Test
    @DisplayName("B.8.b — modifier une élection OPEN 0 vote : OK")
    void modifier_ouvert_zeroVote_ok() {
        ElectionView v = electionService.updateForAdmin(electionId,
                "Titre modifié",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(10));
        assertEquals("Titre modifié", v.getTitle());
    }

    @Test
    @DisplayName("B.8.b — modifier une élection clôturée : refusé")
    void modifier_cloturee_refuse() {
        electionService.closeOnly(electionId);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> electionService.updateForAdmin(electionId,
                        "Nouveau titre",
                        LocalDateTime.now().plusDays(2),
                        LocalDateTime.now().plusDays(10)));
        assertTrue(ex.getMessage().toLowerCase().contains("clotur") ||
                   ex.getMessage().toLowerCase().contains("publi"),
                "Message attendu mentionne statut, trouvé : " + ex.getMessage());
    }

    @Test
    @DisplayName("B.8.b — dépublier une élection publiée : OK")
    void depublier_publiee_ok() {
        // 3 votes distincts (3 users distincts) pour atteindre 100% participation
        // (le mock compte 3 titulaires actifs).
        ElectionView v0 = electionService.get(electionId);
        Long candA = v0.getCandidates().get(0).getId();
        for (long uid : new long[]{101L, 102L, 103L}) {
            UserContext.setCurrentUserId(uid);
            electionService.vote(electionId, candA);
        }
        UserContext.setCurrentUserId(999L); // restaure admin
        electionService.closeOnly(electionId);
        electionService.publishResults(electionId);
        assertTrue(electionRepository.findById(electionId).orElseThrow().isPublished());

        ElectionView v = electionService.unpublishForAdmin(electionId);
        assertFalse(v.isPublished(), "L'élection aurait dû être dépubliée");
        // Status reste CLOSED (la dépublication ne réouvre pas)
        assertEquals(ElectionStatus.CLOSED,
                electionRepository.findById(electionId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("B.8.b — dépublier une élection non publiée : refusé")
    void depublier_nonPubliee_refuse() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> electionService.unpublishForAdmin(electionId));
        assertTrue(ex.getMessage().toLowerCase().contains("publi"),
                "Message attendu mentionne 'publi', trouvé : " + ex.getMessage());
    }
}
