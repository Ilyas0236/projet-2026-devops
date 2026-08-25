package com.wydad.digital.election.repository;

import com.wydad.digital.election.model.ElectionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElectionCandidateRepository extends JpaRepository<ElectionCandidate, Long> {

    List<ElectionCandidate> findByElectionIdOrderByDisplayOrderAscIdAsc(Long electionId);
}
