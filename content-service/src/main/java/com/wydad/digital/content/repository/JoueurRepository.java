package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.Joueur;
import com.wydad.digital.content.model.SportSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JoueurRepository extends JpaRepository<Joueur, Long> {
    List<Joueur> findBySport(SportSection sport);
    List<Joueur> findByPoste(String poste);
}