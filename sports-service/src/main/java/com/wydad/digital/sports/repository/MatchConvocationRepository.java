package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.MatchConvocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchConvocationRepository extends JpaRepository<MatchConvocation, Long> {

    List<MatchConvocation> findByMatchIdOrderByPlayerRoleAscIdAsc(Long matchId);

    List<MatchConvocation> findByJoueurUserIdOrderByMatchIdDesc(Long joueurUserId);

    boolean existsByMatchIdAndJoueurUserId(Long matchId, Long joueurUserId);

    /** Feuilles de match d'un groupe, pour la vue Admin (§9). */
    List<MatchConvocation> findBySportTypeAndCategoryAndStatus(
            SportType sportType, Category category, MatchConvocation.PublicationStatus status);

    List<MatchConvocation> findByStatus(MatchConvocation.PublicationStatus status);

    /**
     * Historique admin : toutes les feuilles traitées (PUBLIEE + REFUSEE).
     * Trié du plus récent au plus ancien pour affichage direct côté front
     * (cf. AdminConvocationsComponent section "Historique").
     */
    List<MatchConvocation> findByStatusInOrderByIdDesc(
            java.util.Collection<MatchConvocation.PublicationStatus> statuses);
}
