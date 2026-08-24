package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.RapportFinancier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RapportFinancierRepository extends JpaRepository<RapportFinancier, Long> {
    /** Tri : exercice le plus récent d'abord. */
    List<RapportFinancier> findAllByOrderByAnneeDescPublieLeDesc();
}
