package com.wydad.digital.gamification.repository;

import com.wydad.digital.gamification.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserIdOrderByAwardedAtDesc(Long userId);

    boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);
}
