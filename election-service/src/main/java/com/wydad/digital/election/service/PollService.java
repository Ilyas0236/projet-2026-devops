package com.wydad.digital.election.service;

import com.wydad.digital.election.dto.PollDtos.CreatePollRequest;
import com.wydad.digital.election.dto.PollDtos.PollResponse;
import com.wydad.digital.election.filter.UserContext;
import com.wydad.digital.election.model.Poll;
import com.wydad.digital.election.model.PollVote;
import com.wydad.digital.election.repository.PollRepository;
import com.wydad.digital.election.repository.PollVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Logique métier des sondages. Identité du votant = contexte JWT
 * (X-User-Id transmis par la passerelle), JAMAIS le body de la requête.
 * Mêmes règles de vote unique que l'élection présidentielle.
 */
@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;

    /** Création d'un sondage (réservée ADMIN via @PreAuthorize du contrôleur). */
    @Transactional
    public PollResponse createPoll(CreatePollRequest request, String adminEmail) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("La question du sondage est obligatoire");
        }
        if (request.options() == null || request.options().size() < 2) {
            throw new IllegalArgumentException("Un sondage doit proposer au moins 2 options");
        }
        boolean anyBlank = request.options().stream().anyMatch(o -> o == null || o.isBlank());
        if (anyBlank) {
            throw new IllegalArgumentException("Les options ne peuvent pas être vides");
        }

        Poll poll = pollRepository.save(Poll.builder()
                .question(request.question().trim())
                .options(request.options().stream().map(String::trim).toList())
                .closesAt(request.closesAt())
                .active(true)
                .createdByEmail(adminEmail)
                .build());

        return toResponse(poll, null);
    }

    /**
     * Sondages actifs. Lecture publique (page /sondages du site officiel,
     * visiteur non connecté inclus) ; l'identité n'est utilisée que pour
     * enrichir la réponse avec le vote de l'utilisateur courant.
     */
    @Transactional(readOnly = true)
    public List<PollResponse> getActivePolls() {
        Long userId = UserContext.getCurrentUserId();
        return pollRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(poll -> toResponse(poll,
                        userId == null ? null
                                : pollVoteRepository.findByPollIdAndUserId(poll.getId(), userId)
                                .map(PollVote::getOptionIndex).orElse(null)))
                .toList();
    }

    /**
     * Vote de l'utilisateur courant. La contrainte d'unicité en base
     * (poll_id, user_id) est le dernier rempart contre le double vote
     * même en concurrence.
     */
    @Transactional
    public PollResponse vote(Long pollId, int optionIndex) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }

        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Sondage non trouvé"));

        if (!poll.isActive()) {
            throw new IllegalStateException("Ce sondage est clôturé");
        }
        if (poll.getClosesAt() != null && LocalDateTime.now().isAfter(poll.getClosesAt())) {
            throw new IllegalStateException("Ce sondage est clos depuis le " + poll.getClosesAt());
        }
        if (optionIndex < 0 || optionIndex >= poll.getOptions().size()) {
            throw new IllegalArgumentException("Option invalide pour ce sondage");
        }
        if (pollVoteRepository.findByPollIdAndUserId(pollId, userId).isPresent()) {
            throw new IllegalStateException("Vous avez déjà voté sur ce sondage");
        }

        try {
            pollVoteRepository.save(PollVote.builder()
                    .poll(poll)
                    .userId(userId)
                    .optionIndex(optionIndex)
                    .build());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Course gagnée par un double vote simultané : la contrainte SQL tranche.
            throw new IllegalStateException("Vous avez déjà voté sur ce sondage");
        }

        return toResponse(poll, optionIndex);
    }

    /** Clôture manuelle par l'ADMIN. */
    @Transactional
    public PollResponse closePoll(Long pollId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Sondage non trouvé"));
        poll.setActive(false);
        return toResponse(pollRepository.save(poll), null);
    }

    private PollResponse toResponse(Poll poll, Integer myVoteIndex) {
        List<Long> results = new ArrayList<>();
        for (int i = 0; i < poll.getOptions().size(); i++) {
            results.add(pollVoteRepository.countByPollIdAndOptionIndex(poll.getId(), i));
        }
        return PollResponse.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .options(poll.getOptions())
                .active(poll.isActive())
                .closesAt(poll.getClosesAt())
                .createdAt(poll.getCreatedAt())
                .totalVotes(results.stream().mapToLong(Long::longValue).sum())
                .resultsPerOption(results)
                .myVoteIndex(myVoteIndex)
                .build();
    }
}
