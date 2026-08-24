package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.Trophy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrophyRepository extends JpaRepository<Trophy, Long> {

    /** Trophées actifs triés pour l'affichage public. */
    List<Trophy> findByActiveTrueOrderByDisplayOrderAsc();

    List<Trophy> findAllByOrderByDisplayOrderAsc();
}
