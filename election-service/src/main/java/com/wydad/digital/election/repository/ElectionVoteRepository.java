package com.wydad.digital.election.repository;

import com.wydad.digital.election.model.ElectionVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ElectionVoteRepository extends JpaRepository<ElectionVote, Long> {

    Optional<ElectionVote> findByElectionIdAndUserId(Long electionId, Long userId);

    long countByElectionIdAndCandidateId(Long electionId, Long candidateId);
}
