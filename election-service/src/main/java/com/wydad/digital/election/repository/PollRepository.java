package com.wydad.digital.election.repository;

import com.wydad.digital.election.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollRepository extends JpaRepository<Poll, Long> {

    List<Poll> findByActiveTrueOrderByCreatedAtDesc();
}
