package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.MatchStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchStatRepository extends JpaRepository<MatchStat, Long> {

    List<MatchStat> findByJoueurUserIdOrderByMatchDateDesc(Long joueurUserId);
}
