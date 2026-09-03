package com.wydad.digital.gamification.repository;

import com.wydad.digital.gamification.model.PointRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointRuleRepository extends JpaRepository<PointRule, Long> {
    Optional<PointRule> findByRuleKey(String ruleKey);
    List<PointRule> findAllByOrderByRuleKeyAsc();
}
