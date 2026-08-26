package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.Match;
import com.wydad.digital.content.model.MatchCategory;
import com.wydad.digital.content.model.MatchStatut;
import com.wydad.digital.content.model.SportSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByStatut(MatchStatut statut);
    List<Match> findBySport(SportSection sport);
    List<Match> findBySportAndStatut(SportSection sport, MatchStatut statut);
    List<Match> findBySportAndCategorie(SportSection sport, MatchCategory categorie);
}