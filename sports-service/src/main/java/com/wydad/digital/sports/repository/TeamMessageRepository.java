package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.TeamMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TeamMessageRepository extends JpaRepository<TeamMessage, Long> {

    /** Historique complet d'un groupe, du plus ancien au plus récent. */
    List<TeamMessage> findBySportTypeAndCategoryOrderByCreatedAtAsc(SportType sportType, Category category);

    /**
     * Derniers messages d'un groupe (pour l'affichage « WhatsApp » :
     * on ne recharge pas tout l'historique à l'ouverture).
     */
    List<TeamMessage> findBySportTypeAndCategoryOrderByCreatedAtDesc(SportType sportType, Category category,
                                                                     Pageable pageable);
}
