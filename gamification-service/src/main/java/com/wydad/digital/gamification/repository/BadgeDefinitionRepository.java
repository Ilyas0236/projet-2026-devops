package com.wydad.digital.gamification.repository;

import com.wydad.digital.gamification.model.BadgeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BadgeDefinitionRepository extends JpaRepository<BadgeDefinition, Long> {

    Optional<BadgeDefinition> findByCode(String code);

    List<BadgeDefinition> findAllByOrderByMinPointsAsc();

    List<BadgeDefinition> findByActiveTrueOrderByMinPointsAsc();
}
