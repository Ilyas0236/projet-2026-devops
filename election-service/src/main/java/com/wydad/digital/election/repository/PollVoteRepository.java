package com.wydad.digital.election.repository;

import com.wydad.digital.election.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    Optional<PollVote> findByPollIdAndUserId(Long pollId, Long userId);

    long countByPollIdAndOptionIndex(Long pollId, int optionIndex);

    long countByPollId(Long pollId);
}
