package com.wydad.digital.election;

import com.wydad.digital.election.model.Election;
import com.wydad.digital.election.model.ElectionStatus;
import com.wydad.digital.election.repository.ElectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ISTQB — Élections présidentielles, techniques appliquées :
 *
 *  1. Transitions d'état : OPEN -> CLOSED irréversible (pas de réouverture,
 *     pas de vote ni de candidat après clôture).
 *  2. Tables de décision : rôles × routes (public / membre / ADMIN).
 *  3. Valeurs aux limites : bornes exactes de la fenêtre de vote,
 *     candidats < 2, dates incohérentes.
 *  4. Partition d'équivalence : anonyme / membre / ADMIN ; candidat de
 *     l'élection vs candidat d'une autre élection.
 *  5. Vote unique : garde applicative + contrainte SQL (double vote refusé).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ElectionLifecycleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ElectionRepository electionRepository;
    @Autowired com.wydad.digital.election.service.ElectionService electionService;

    private static final String ADMIN_EMAIL = "president.bureau@wydad.ma";

    @Autowired com.wydad.digital.election.repository.ElectionVoteRepository voteRepository;
    @Autowired com.wydad.digital.election.repository.ElectionCandidateRepository candidateRepository;

    @BeforeEach
    void cleanup() {
        // H2 DB_CLOSE_DELAY=-1 : la base survit entre tests -> nettoyage
        // complet dans l'ordre des clés étrangères.
        voteRepository.deleteAll();
        candidateRepository.deleteAll();
        electionRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** En-têtes gateway : appliqués EN DERNIER dans la chaîne fluent. */
    private MockHttpServletRequestBuilder asGateway(MockHttpServletRequestBuilder b, Long id, String email, String role) {
        if (id != null) b.header("X-User-Id", id);
        if (email != null) b.header("X-User-Email", email);
        if (role != null) b.header("X-User-Role", role);
        return b;
    }

    private long createElection(LocalDateTime startsAt, LocalDateTime endsAt) throws Exception {
        String body = """
                {
                  "title": "Élection du président 2026",
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "candidates": [
                    { "fullName": "Candidat A", "presentation": "Projet sportif" },
                    { "fullName": "Candidat B", "presentation": "Projet social" }
                  ]
                }
                """.formatted(startsAt, endsAt);
        MvcResult result = mockMvc.perform(asGateway(post("/api/elections"), null, ADMIN_EMAIL, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createOpenElectionWithVotingWindow() throws Exception {
        return createElection(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(2));
    }

    // ------------------------------------------------------------------
    // Création — valeurs aux limites & validations
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Création par ADMIN : 201 avec candidats")
    void creationParAdmin() throws Exception {
        mockMvc.perform(asGateway(post("/api/elections"), null, ADMIN_EMAIL, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Élection du président 2026",
                                  "startsAt": "%s",
                                  "endsAt": "%s",
                                  "candidates": [
                                    {"fullName": "Candidat A"},
                                    {"fullName": "Candidat B"}
                                  ]
                                }
                                """.formatted(LocalDateTime.now(), LocalDateTime.now().plusDays(7))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.candidates.length()").value(2));
    }

    @Test
    @DisplayName("Création sans titre : 400")
    void creationSansTitre400() throws Exception {
        mockMvc.perform(asGateway(post("/api/elections"), null, ADMIN_EMAIL, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "startsAt": "%s",
                                  "endsAt": "%s",
                                  "candidates": [
                                    {"fullName": "A"},
                                    {"fullName": "B"}
                                  ]
                                }
                                """.formatted(LocalDateTime.now(), LocalDateTime.now().plusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Dates incohérentes (fin avant début) : 400")
    void datesIncoherentes400() throws Exception {
        mockMvc.perform(asGateway(post("/api/elections"), null, ADMIN_EMAIL, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Élection inversée",
                                  "startsAt": "%s",
                                  "endsAt": "%s",
                                  "candidates": [
                                    {"fullName": "A"},
                                    {"fullName": "B"}
                                  ]
                                }
                                """.formatted(LocalDateTime.now().plusDays(2), LocalDateTime.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Moins de 2 candidats : 400")
    void unSeulCandidat400() throws Exception {
        mockMvc.perform(asGateway(post("/api/elections"), null, ADMIN_EMAIL, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mono-candidate",
                                  "startsAt": "%s",
                                  "endsAt": "%s",
                                  "candidates": [{"fullName": "Unique"}]
                                }
                                """.formatted(LocalDateTime.now(), LocalDateTime.now().plusDays(1))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Table de décision rôles × routes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Non-ADMIN ne peut pas créer d'élection : 403")
    void creationParNonAdmin403() throws Exception {
        mockMvc.perform(asGateway(post("/api/elections"), 501L, "membre@wydad.ma", "ADHERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Coup d'état",
                                  "startsAt": "%s",
                                  "endsAt": "%s",
                                  "candidates": [
                                    {"fullName": "Moi"},
                                    {"fullName": "Mon ami"}
                                  ]
                                }
                                """.formatted(LocalDateTime.now(), LocalDateTime.now().plusDays(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Résultats publiés accessibles SANS connexion (site public)")
    void resultatsPublicsSansAuth() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        vote(id, 101L, firstCandidateId(id));

        mockMvc.perform(asGateway(post("/api/elections/" + id + "/close"), null, ADMIN_EMAIL, "ADMIN"))
                .andExpect(status().isOk());

        // Aucun en-tête X-User-* : le visiteur du site officiel voit le gagnant.
        mockMvc.perform(get("/api/elections/published/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.totalVotes").value(1));
    }

    // ------------------------------------------------------------------
    // Vote — fenêtre temporelle (valeurs aux limites) et unicité
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Vote dans la fenêtre : OK puis double vote : 409")
    void votePuisDoubleVote409() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        long candidateId = firstCandidateId(id);
        vote(id, 201L, candidateId);
        // Même utilisateur, second vote : refuse par garde applicative.
        mockMvc.perform(asGateway(post("/api/elections/" + id + "/vote"), 201L, "votant@wydad.ma", "ADHERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": " + candidateId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Vote hors fenêtre (avant ouverture) : 409")
    void voteAvantOuverture409() throws Exception {
        long id = createElection(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3));
        mockMvc.perform(asGateway(post("/api/elections/" + id + "/vote"), 301L, "impatient@wydad.ma", "ADHERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidatePayload(firstCandidateId(id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ouvre")));
    }

    @Test
    @DisplayName("Vote pour un candidat d'une autre élection : 400")
    void voteCandidatAutreElection400() throws Exception {
        long e1 = createOpenElectionWithVotingWindow();
        long e2 = createOpenElectionWithVotingWindow();
        long candidateOfE2 = firstCandidateId(e2);

        mockMvc.perform(asGateway(post("/api/elections/" + e1 + "/vote"), 401L, "electeur@wydad.ma", "ADHERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": " + candidateOfE2 + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Anonyme ne peut pas voter : 401/403")
    void voteAnonymeRefuse() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        mockMvc.perform(post("/api/elections/" + id + "/vote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidatePayload(firstCandidateId(id))))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // Clôture — transition d'état irréversible
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Clôture manuelle : publication des résultats, gagnant majoritaire relatif")
    void clotureManuellePublieGagnant() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        long c0 = firstCandidateId(id);

        vote(id, 601L, c0);
        vote(id, 602L, c0);
        vote(id, 603L, firstOtherCandidateId(id));

        MvcResult closed = mockMvc.perform(asGateway(post("/api/elections/" + id + "/close"), null, ADMIN_EMAIL, "ADMIN"))
                .andExpect(status().isOk())
                .andReturn();
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(closed.getResponse().getContentAsString());

        assertThat(json.get("status").asText()).isEqualTo("CLOSED");
        assertThat(json.get("published").asBoolean()).isTrue();
        assertThat(json.get("winnerCandidateId").asLong()).isEqualTo(c0); // 2 voix > 1

        int p0 = json.get("percentages").get(0).asInt();
        assertThat(p0).isEqualTo(67); // 2/3 arrondi

        // L'élection clôturée apparaît bien côté public.
        mockMvc.perform(get("/api/elections/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].winnerCandidateId").value(c0));
    }

    @Test
    @DisplayName("Après clôture : plus de vote possible (409), plus de candidat ajoutable (409)")
    void apresClotureToutEstFige() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        mockMvc.perform(asGateway(post("/api/elections/" + id + "/close"), null, ADMIN_EMAIL, "ADMIN"))
                .andExpect(status().isOk());

        mockMvc.perform(asGateway(post("/api/elections/" + id + "/vote"), 701L, "tardif@wydad.ma", "ADHERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidatePayload(firstCandidateId(id))))
                .andExpect(status().isConflict());

        mockMvc.perform(asGateway(post("/api/elections/" + id + "/candidates"), null, ADMIN_EMAIL, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Arriviste\"}"))
                .andExpect(status().isConflict());

        // Re-clôture idempotente : pas d'erreur.
        mockMvc.perform(asGateway(post("/api/elections/" + id + "/close"), null, ADMIN_EMAIL, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("Clôture par non-ADMIN : 403")
    void clotureParNonAdmin403() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        mockMvc.perform(asGateway(post("/api/elections/" + id + "/close"), 801L, "membre@wydad.ma", "ADHERENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Élection sans aucun vote : publiée sans gagnant (winnerCandidateId null)")
    void electionSansVotePublieeSansGagnant() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        mockMvc.perform(asGateway(post("/api/elections/" + id + "/close"), null, ADMIN_EMAIL, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.winnerCandidateId").doesNotExist())
                .andExpect(jsonPath("$.totalVotes").value(0));
    }

    // ------------------------------------------------------------------
    // Candidats
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Retrait d'un candidat ayant des votes : 409 ; sans votes : OK")
    void retraitCandidatSelonVotes() throws Exception {
        long id = createOpenElectionWithVotingWindow();
        long c0 = firstCandidateId(id);
        long c1 = firstOtherCandidateId(id);

        vote(id, 901L, c0);

        // c0 a un vote : retrait refusé.
        mockMvc.perform(asGateway(delete("/api/elections/" + id + "/candidates/" + c0), null, ADMIN_EMAIL, "ADMIN"))
                .andExpect(status().isConflict());

        // c1 n'a aucun vote : retrait accepté.
        mockMvc.perform(asGateway(delete("/api/elections/" + id + "/candidates/" + c1), null, ADMIN_EMAIL, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(1));
    }

    // ------------------------------------------------------------------
    // Scheduler : clôture automatique à la date de fin
    // ------------------------------------------------------------------

    @Test
    @DisplayName("closeExpiredElections : clôture automatique des élections échues")
    void clotureAutomatiqueDesEchues() throws Exception {
        // Élection déjà expirée (fin passée mais encore OPEN).
        long id = createElection(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusHours(1));

        // On invoque directement la méthode du bean réel du contexte Spring
        // (le déclenchement temporel @Scheduled lui-même n'est pas testé).
        electionService.closeExpiredElections();

        Election e = electionRepository.findById(id).orElseThrow();
        assertThat(e.getStatus()).isEqualTo(ElectionStatus.CLOSED);
        assertThat(e.isPublished()).isTrue();
    }

    // ------------------------------------------------------------------
    // Petits utilitaires
    // ------------------------------------------------------------------

    private void vote(long electionId, long userId, long candidateId) throws Exception {
        mockMvc.perform(asGateway(post("/api/elections/" + electionId + "/vote"), userId,
                        "user" + userId + "@wydad.ma", "ADHERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": " + candidateId + "}"))
                .andExpect(status().isOk());
    }

    private String candidatePayload(long candidateId) {
        return "{\"candidateId\": " + candidateId + "}";
    }

    private long firstCandidateId(long electionId) throws Exception {
        MvcResult result = mockMvc
                .perform(asGateway(get("/api/elections/" + electionId), 999L, "lecteur@wydad.ma", "ADHERENT"))
                .andExpect(status().isOk()).andReturn();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("candidates").get(0).get("id").asLong();
    }

    private long firstOtherCandidateId(long electionId) throws Exception {
        MvcResult result = mockMvc
                .perform(asGateway(get("/api/elections/" + electionId), 999L, "lecteur@wydad.ma", "ADHERENT"))
                .andExpect(status().isOk()).andReturn();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("candidates").get(1).get("id").asLong();
    }
}
