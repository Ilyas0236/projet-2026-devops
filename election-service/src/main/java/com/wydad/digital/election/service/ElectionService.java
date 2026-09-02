package com.wydad.digital.election.service;

import com.wydad.digital.election.client.ActiveMembersClient;
import com.wydad.digital.election.client.AuthSubscriptionClient;
import com.wydad.digital.election.client.NotificationClient;
import com.wydad.digital.election.dto.ElectionDtos.AddCandidateRequest;
import com.wydad.digital.election.dto.ElectionDtos.CandidateView;
import com.wydad.digital.election.dto.ElectionDtos.CreateElectionRequest;
import com.wydad.digital.election.dto.ElectionDtos.ElectionView;
import com.wydad.digital.election.filter.UserContext;
import com.wydad.digital.election.model.Election;
import com.wydad.digital.election.model.ElectionCandidate;
import com.wydad.digital.election.model.ElectionStatus;
import com.wydad.digital.election.model.ElectionVote;
import com.wydad.digital.election.repository.ElectionCandidateRepository;
import com.wydad.digital.election.repository.ElectionRepository;
import com.wydad.digital.election.repository.ElectionVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Élections du président du club.
 *
 * Cycle : ADMIN crée (période + candidats) -> adhérents votent UNE fois
 * pendant la fenêtre -> à endsAt le scheduler clôture, calcule les résultats
 * et les publie (gagnant + pourcentages) -> NotificationClient prévient les
 * adhérents. Clôture manuelle ADMIN possible avant terme.
 *
 * Sécurité des résultats : les comptages ne sortent JAMAIS du serveur tant
 * que l'élection n'est pas clôturée — pas de « résultats en temps réel »
 * côté client, seule l'ADMIN les consulte via /{id}. C'est la règle
 * électorale classique : pas d'influence possible sur le vote en cours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final ElectionCandidateRepository candidateRepository;
    private final ElectionVoteRepository voteRepository;
    private final NotificationClient notificationClient;
    private final AuthSubscriptionClient authSubscriptionClient;
    private final ActiveMembersClient activeMembersClient;

    // ------------------------------------------------------------------
    // Administration
    // ------------------------------------------------------------------

    /** Création d'une élection avec sa période et ses candidats (ADMIN). */
    @Transactional
    public ElectionView create(CreateElectionRequest request, String adminEmail) {
        if (request.getStartsAt().isAfter(request.getEndsAt())
                || request.getStartsAt().isEqual(request.getEndsAt())) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début");
        }
        if (request.getCandidates() == null || request.getCandidates().size() < 2) {
            throw new IllegalArgumentException("Une élection doit compter au moins 2 candidats");
        }

        Election election = electionRepository.save(Election.builder()
                .title(request.getTitle().trim())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .status(ElectionStatus.OPEN)
                .published(false)
                .createdByEmail(adminEmail)
                .build());

        for (AddCandidateRequest input : request.getCandidates()) {
            saveCandidate(election, input);
        }
        log.info("Election {} creee par {} ({} candidats)", election.getId(), adminEmail,
                request.getCandidates().size());
        return toView(election, UserContext.getCurrentUserId());
    }

    /** Ajout d'un candidat — interdit une fois l'élection clôturée. */
    @Transactional
    public ElectionView addCandidate(Long electionId, AddCandidateRequest request) {
        Election election = getElection(electionId);
        if (election.getStatus() != ElectionStatus.OPEN) {
            throw new IllegalStateException("Impossible d'ajouter un candidat à une élection clôturée");
        }
        saveCandidate(election, request);
        return toView(election, UserContext.getCurrentUserId());
    }

    /** Suppression d'un candidat — uniquement tant que personne n'a voté pour lui. */
    @Transactional
    public ElectionView removeCandidate(Long electionId, Long candidateId) {
        Election election = getElection(electionId);
        if (election.getStatus() != ElectionStatus.OPEN) {
            throw new IllegalStateException("Impossible de retirer un candidat d'une élection clôturée");
        }
        ElectionCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidat non trouvé"));
        if (!candidate.getElection().getId().equals(electionId)) {
            throw new IllegalArgumentException("Ce candidat n'appartient pas à cette élection");
        }
        long votes = voteRepository.countByElectionIdAndCandidateId(electionId, candidateId);
        if (votes > 0) {
            throw new IllegalStateException(
                    "Ce candidat a déjà reçu " + votes + " vote(s) : il ne peut plus être retiré");
        }
        candidateRepository.delete(candidate);
        return toView(election, UserContext.getCurrentUserId());
    }

    /**
     * Clôture manuelle (ADMIN, ex. avant terme). Calcule et publie les
     * résultats immédiatement. Transition OPEN -> CLOSED irréversible.
     */
    @Transactional
    public ElectionView closeAndPublish(Long electionId) {
        return closeAndPublishInternal(getElection(electionId));
    }

    // ------------------------------------------------------------------
    // Vote
    // ------------------------------------------------------------------

    /**
     * Vote de l'utilisateur courant. L'identifiant vient du contexte JWT —
     * on ne vote jamais au nom d'un autre. Un seul vote par élection :
     * garde applicative + contrainte SQL unique (election_id, user_id).
     */
    @Transactional
    public ElectionView vote(Long electionId, Long candidateId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }

        Election election = getElection(electionId);
        if (election.getStatus() != ElectionStatus.OPEN) {
            throw new IllegalStateException("Cette élection est clôturée");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(election.getStartsAt())) {
            throw new IllegalStateException("Le vote ouvre le " + election.getStartsAt());
        }
        if (now.isAfter(election.getEndsAt())) {
            throw new IllegalStateException("Le vote est clos depuis le " + election.getEndsAt());
        }

        // B.18 — condition d'éligibilité au vote : l'utilisateur doit avoir
        // un abonnement saisonnier ACTIF (soutien effectif au club). Les
        // sondages (PollService) ne sont PAS soumis à cette condition —
        // c'est une règle spécifique à l'élection présidentielle. ADMIN
        // est exempté (peut voter sans abonnement pour ne pas bloquer
        // l'organisation interne en cas de pépin technique auth-service).
        String currentEmail = UserContext.getCurrentUserEmail();
        if (!UserContext.isAdmin()
                && !authSubscriptionClient.isActiveSubscriber(currentEmail)) {
            throw new IllegalStateException("VOTE_REQUIRES_MEMBERSHIP");
        }

        ElectionCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidat non trouvé"));
        if (!candidate.getElection().getId().equals(electionId)) {
            throw new IllegalArgumentException("Ce candidat ne participe pas à cette élection");
        }

        if (voteRepository.findByElectionIdAndUserId(electionId, userId).isPresent()) {
            throw new IllegalStateException("Vous avez déjà voté pour cette élection");
        }
        try {
            voteRepository.save(ElectionVote.builder()
                    .election(election)
                    .userId(userId)
                    .candidate(candidate)
                    .build());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Double vote simultané : la contrainte SQL tranche.
            throw new IllegalStateException("Vous avez déjà voté pour cette élection");
        }

        return toView(election, userId);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /**
     * Élections publiées (résultats visibles SANS connexion — site public),
     * les plus récentes d'abord.
     */
    @Transactional(readOnly = true)
    public List<ElectionView> getPublished() {
        return electionRepository.findByStatus(ElectionStatus.CLOSED).stream()
                .filter(Election::isPublished)
                .map(e -> toView(e, null))
                .toList();
    }

    /** Dernière élection publiée — endpoint public du site officiel. */
    @Transactional(readOnly = true)
    public ElectionView getLatestPublished() {
        return electionRepository.findByStatusOrderByStartsAtDesc(ElectionStatus.CLOSED).stream()
                .filter(Election::isPublished)
                .findFirst()
                .map(e -> toView(e, null))
                .orElse(null);
    }

    /** Élections ouvertes pour l'espace adhérent (avec son état de vote). */
    @Transactional(readOnly = true)
    public List<ElectionView> getOpenForCurrentUser() {
        return electionRepository.findByStatus(ElectionStatus.OPEN).stream()
                .map(e -> toView(e, UserContext.getCurrentUserId()))
                .toList();
    }

    /** Détail d'une élection ; résultats exposés seulement si publiés. */
    @Transactional(readOnly = true)
    public ElectionView get(Long id) {
        Long userId = UserContext.getCurrentUserId();
        Election election = getElection(id);
        if (election.isPublished()) {
            return toView(election, userId);
        }
        // Non publiée : visible (candidats/période) mais sans aucun comptage.
        // B.8 — on garde la participation X/Y même avant publication
        // (transparence demandée) mais SANS winnerCandidateId, results
        // ou percentages (toujours secrets avant clôture).
        ElectionView full = toView(election, userId);
        return ElectionView.builder()
                .id(full.getId()).title(full.getTitle())
                .startsAt(full.getStartsAt()).endsAt(full.getEndsAt())
                .status(full.getStatus()).published(false)
                .winnerCandidateId(null)
                .candidates(full.getCandidates())
                .totalVotes(0).results(List.of()).percentages(List.of())
                .myVoteIndex(full.getMyVoteIndex())
                .canVote(full.isCanVote())
                .eligibleVotersCount(full.getEligibleVotersCount())
                .participationPercent(full.getParticipationPercent())
                .build();
    }

    // ------------------------------------------------------------------
    // Clôture automatique (premier scheduler du projet)
    // ------------------------------------------------------------------

    /**
     * Toutes les 60 s : clôture + publication des élections OPEN dont la
     * date de fin est passée. Idempotent — seules les élections encore OPEN
     * sont traitées, donc deux exécutions consécutives ne font rien de plus.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeExpiredElections() {
        LocalDateTime now = LocalDateTime.now();
        List<Election> expired = electionRepository.findByStatus(ElectionStatus.OPEN).stream()
                .filter(e -> now.isAfter(e.getEndsAt()))
                .toList();
        for (Election election : expired) {
            try {
                closeAndPublishInternal(election);
                log.info("Election {} cloturee automatiquement (fin {})", election.getId(), election.getEndsAt());
            } catch (Exception e) {
                // Une élection en échec ne bloque jamais les autres ni le scheduler.
                log.error("Echec de cloture automatique de l'élection {}", election.getId(), e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Publication des résultats
    // ------------------------------------------------------------------

    /**
     * Dépouillement : comptage par candidat, gagnant = majorité relative
     * (en cas d'égalité, le premier atteint est gardé), pourcentages arrondis,
     * figeage du gagnant, publication, puis notification IN_APP best-effort.
     * Transition irréversible : status=CLOSED + published=true en un seul
     * flush transactionnel.
     */
    private ElectionView closeAndPublishInternal(Election election) {
        if (election.getStatus() == ElectionStatus.CLOSED) {
            // Idempotence : déjà clôturée, on renvoie juste l'état publié.
            return toView(election, UserContext.getCurrentUserId());
        }

        List<ElectionCandidate> candidates =
                candidateRepository.findByElectionIdOrderByDisplayOrderAscIdAsc(election.getId());
        List<Long> results = new ArrayList<>();
        long total = 0;
        Long winnerId = null;
        long winnerVotes = -1;
        for (ElectionCandidate c : candidates) {
            long votes = voteRepository.countByElectionIdAndCandidateId(election.getId(), c.getId());
            results.add(votes);
            total += votes;
            if (votes > winnerVotes) {
                winnerVotes = votes;
                winnerId = c.getId();
            }
        }
        if (total == 0) winnerId = null; // aucun vote exprimé : pas de gagnant

        List<Integer> percentages = computePercentages(results, total);

        election.setStatus(ElectionStatus.CLOSED);
        election.setPublished(true);
        election.setWinnerCandidateId(winnerId);
        electionRepository.save(election);

        notifyMembers(election, candidates, winnerId, results.indexOf(winnerId), percentages);
        return toView(election, UserContext.getCurrentUserId());
    }

    /**
     * Notification de publication (best-effort) : message global avec le nom
     * du gagnant et son pourcentage ; broadcast à tous les membres actifs
     * (préférences IN_APP respectées côté notification-service) + notif
     * dédiée au(x) président(s) en exercice pointant le dashboard président.
     */
    private void notifyMembers(Election election, List<ElectionCandidate> candidates,
                               Long winnerId, int winnerIndex, List<Integer> percentages) {
        try {
            String winnerName = winnerIndex >= 0 ? candidates.get(winnerIndex).getFullName() : null;
            String detail = winnerName != null
                    ? winnerName + " est élu président avec "
                        + percentages.get(winnerIndex) + "% des voix."
                    : "Aucun vote exprimé : aucun gagnant.";
            String title = "Résultats de l'élection publiés";
            String message = "Élection « " + election.getTitle() + " » : " + detail;
            // 1) Broadcast à tous les membres (le /internal/broadcast fait
            //    le fan-out avec respect des préférences individuelles).
            notificationClient.notifyBroadcast(title, message, "/elections/resultats");
            // 2) Notification ciblée au(x) président(s) en exercice — la
            //    cloche de leur topbar s'allumera pour pointer leur dashboard.
            //    On notifie via un appel broadcast qui passera par tous les
            //    comptes PRESIDENT actifs ; on garde le fan-out identique
            //    (pas de distinction), le message est de toute façon utile
            //    à tous les rôles.
        } catch (Exception e) {
            log.warn("Notification de publication non envoyee pour l'élection {}: {}",
                    election.getId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void saveCandidate(Election election, AddCandidateRequest input) {
        if (input.getFullName() == null || input.getFullName().isBlank()) {
            throw new IllegalArgumentException("Le nom du candidat est obligatoire");
        }
        // B.8 — si l'admin a lié un titulaire (userId) au candidat, on
        // refuse d'avance les doublons (election, userId). La contrainte
        // SQL uk_election_candidate_user est un filet de sécurité mais
        // on préfère un 409 propre côté service. Les userId null
        // (rétro-compat) ne déclenchent pas ce check.
        if (input.getUserId() != null
                && candidateRepository.findByElectionIdAndUserId(election.getId(), input.getUserId()).isPresent()) {
            throw new IllegalStateException(
                    "Ce titulaire est déjà candidat à cette élection (userId=" + input.getUserId() + ")");
        }
        int order = input.getDisplayOrder() != null ? input.getDisplayOrder() : nextDisplayOrder(election.getId());
        candidateRepository.save(ElectionCandidate.builder()
                .election(election)
                .fullName(input.getFullName().trim())
                .presentation(input.getPresentation())
                .photoUrl(input.getPhotoUrl())
                .displayOrder(order)
                .userId(input.getUserId())
                .build());
    }

    private int nextDisplayOrder(Long electionId) {
        var last = candidateRepository.findByElectionIdOrderByDisplayOrderAscIdAsc(electionId);
        return last.isEmpty() ? 0 : last.get(last.size() - 1).getDisplayOrder() + 1;
    }

    private Election getElection(Long id) {
        return electionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Élection non trouvée"));
    }

    private List<Integer> computePercentages(List<Long> results, long total) {
        final long t = total;
        return results.stream()
                .map(v -> t == 0 ? 0 : (int) Math.round(v * 100.0 / t))
                .toList();
    }

    /**
     * Construction de la vue. `canVote` = élection ouverte, dans la fenêtre,
     * utilisateur authentifié et n'ayant pas encore voté.
     */
    private ElectionView toView(Election election, Long userId) {
        List<ElectionCandidate> candidates =
                candidateRepository.findByElectionIdOrderByDisplayOrderAscIdAsc(election.getId());

        boolean publishedResults = election.isPublished();
        List<Long> results = new ArrayList<>();
        List<Integer> percentages = List.of();
        long total = 0;
        if (publishedResults) {
            for (ElectionCandidate c : candidates) {
                long v = voteRepository.countByElectionIdAndCandidateId(election.getId(), c.getId());
                results.add(v);
                total += v;
            }
            percentages = computePercentages(results, total);
        }

        Integer myVoteIndex = null;
        boolean hasVoted = false;
        if (userId != null) {
            var mine = voteRepository.findByElectionIdAndUserId(election.getId(), userId);
            if (mine.isPresent()) {
                hasVoted = true;
                Long votedCandidateId = mine.get().getCandidate().getId();
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).getId().equals(votedCandidateId)) {
                        myVoteIndex = i;
                        break;
                    }
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        boolean canVote = !hasVoted
                && election.getStatus() == ElectionStatus.OPEN
                && userId != null
                && !now.isBefore(election.getStartsAt())
                && !now.isAfter(election.getEndsAt());

        List<CandidateView> candidateViews = candidates.stream()
                .map(c -> CandidateView.builder()
                        .id(c.getId())
                        .fullName(c.getFullName())
                        .presentation(c.getPresentation())
                        .photoUrl(c.getPhotoUrl())
                        .displayOrder(c.getDisplayOrder())
                        .userId(c.getUserId())
                        .build())
                .toList();

        // B.8 — participation X/Y.
        //   - Si publiée : on fige le snapshot à endsAt (un adhérent
        //     achetant APRÈS clôture ne doit pas être compté comme
        //     « n'ayant pas voté »).
        //   - Si en cours (OPEN) : snapshot = now() (approximation, on
        //     accepte une petite dérive). C'est cohérent avec la
        //     transparence demandée (« résultats partiels en temps réel »).
        //   - Si 0 éligibles (auth-service down par ex.) : 0% pour
        //     éviter une division par zéro. Le snapshot 0 ne casse pas
        //     l'UI : l'admin verra « Participation 0/? » et saura
        //     investiguer.
        LocalDateTime snapshotAt = election.getEndsAt() != null && !election.getEndsAt().isAfter(now)
                ? election.getEndsAt()
                : now;
        long eligibleVotersCount = activeMembersClient.countActiveAt(snapshotAt);
        int participationPercent = eligibleVotersCount == 0
                ? 0
                : (int) Math.round(total * 100.0 / eligibleVotersCount);

        return ElectionView.builder()
                .id(election.getId())
                .title(election.getTitle())
                .startsAt(election.getStartsAt())
                .endsAt(election.getEndsAt())
                .status(election.getStatus().name())
                .published(election.isPublished())
                .winnerCandidateId(publishedResults ? election.getWinnerCandidateId() : null)
                .candidates(candidateViews)
                .totalVotes(total)
                .results(results)
                .percentages(percentages)
                .myVoteIndex(myVoteIndex)
                .canVote(canVote)
                .eligibleVotersCount(eligibleVotersCount)
                .participationPercent(participationPercent)
                .build();
    }
}
