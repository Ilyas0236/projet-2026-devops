package com.wydad.digital.election.repository;

import com.wydad.digital.election.model.ElectionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ElectionCandidateRepository extends JpaRepository<ElectionCandidate, Long> {

    List<ElectionCandidate> findByElectionIdOrderByDisplayOrderAscIdAsc(Long electionId);

    /**
     * B.8 — Unicité applicative (election, userId) : on refuse deux
     * candidats liés au même titulaire dans la même élection. La
     * contrainte SQL {@code uk_election_candidate_user} est le filet
     * de sécurité final (gérée par {@code ddl-auto: update}).
     */
    Optional<ElectionCandidate> findByElectionIdAndUserId(Long electionId, Long userId);

    /** B.8.b — Suppression en cascade des candidats d'une élection. */
    void deleteByElectionId(Long electionId);
}
