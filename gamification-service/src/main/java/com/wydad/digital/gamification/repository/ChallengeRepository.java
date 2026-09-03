package com.wydad.digital.gamification.repository;

import com.wydad.digital.gamification.model.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    /** Défis actifs dont la fenêtre temporelle inclut maintenant. */
    List<Challenge> findByActiveTrueAndStartsAtBeforeAndEndsAtAfter(
            LocalDateTime nowStart, LocalDateTime nowEnd);

    /** Tous les défis actifs (pour affichage public / espace fan, y compris à venir). */
    List<Challenge> findByActiveTrueOrderByStartsAtDesc();

    /** Tous les défis, y compris inactifs (ADMIN). */
    List<Challenge> findAllByOrderByStartsAtDesc();
}
