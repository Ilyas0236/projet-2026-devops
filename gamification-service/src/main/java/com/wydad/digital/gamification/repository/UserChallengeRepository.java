package com.wydad.digital.gamification.repository;

import com.wydad.digital.gamification.model.UserChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, Long> {

    Optional<UserChallenge> findByUserIdAndChallengeId(Long userId, Long challengeId);

    /** Progression d'un utilisateur sur tous ses défis en cours. */
    List<UserChallenge> findByUserIdAndChallengeIdIn(Long userId, List<Long> challengeIds);

    /** Toutes les progressions d'un défi (admin : qui a presque fini ?). */
    List<UserChallenge> findByChallengeId(Long challengeId);
}
