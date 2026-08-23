package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.Sponsor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SponsorRepository extends JpaRepository<Sponsor, Long> {

    /** Sponsors actifs triés pour l'affichage public. */
    List<Sponsor> findByActiveTrueOrderByDisplayOrderAsc();

    List<Sponsor> findAllByOrderByDisplayOrderAsc();
}
