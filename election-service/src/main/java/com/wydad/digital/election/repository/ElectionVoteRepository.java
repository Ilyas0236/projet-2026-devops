package com.wydad.digital.election.repository;

import com.wydad.digital.election.model.ElectionVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ElectionVoteRepository extends JpaRepository<ElectionVote, Long> {

    Optional<ElectionVote> findByElectionIdAndUserId(Long electionId, Long userId);

    long countByElectionIdAndCandidateId(Long electionId, Long candidateId);

    /**
     * B.8 — Nombre total de votes pour une élection (tous candidats).
     * Utilisé par {@code ElectionService.publishResults} et
     * {@code getPublishEligibility} pour calculer la participation
     * et la condition « tous les éligibles ont voté ».
     */
    long countByElectionId(Long electionId);
}
