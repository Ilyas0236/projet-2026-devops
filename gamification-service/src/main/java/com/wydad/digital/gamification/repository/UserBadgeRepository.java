package com.wydad.digital.gamification.repository;

import com.wydad.digital.gamification.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserIdOrderByAwardedAtDesc(Long userId);

    /** Toutes les attributions de badges, du plus récent au plus ancien. */
    List<UserBadge> findAllByOrderByAwardedAtDesc(org.springframework.data.domain.Pageable pageable);

    boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);
}
