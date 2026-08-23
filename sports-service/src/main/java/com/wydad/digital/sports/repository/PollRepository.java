package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollRepository extends JpaRepository<Poll, Long> {

    List<Poll> findByActiveTrueOrderByCreatedAtDesc();
}
