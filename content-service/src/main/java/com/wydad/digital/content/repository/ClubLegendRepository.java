package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.ClubLegend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClubLegendRepository extends JpaRepository<ClubLegend, Long> {

    /** Légendes actives triées pour l'affichage public. */
    List<ClubLegend> findByActiveTrueOrderByDisplayOrderAsc();

    List<ClubLegend> findAllByOrderByDisplayOrderAsc();
}
