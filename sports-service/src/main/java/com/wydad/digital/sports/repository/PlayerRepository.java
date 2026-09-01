package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByUserId(Long userId);
    List<Player> findBySportTypeAndCategory(SportType sportType, Category category);
    /** C.21 — tous les joueurs d'une discipline (toutes catégories confondues).
     * Utilisé par l'annuaire du président. */
    List<Player> findBySportType(SportType sportType);
}
