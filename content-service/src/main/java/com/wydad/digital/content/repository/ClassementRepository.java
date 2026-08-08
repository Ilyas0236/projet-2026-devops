package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.Classement;
import com.wydad.digital.content.model.SportSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassementRepository extends JpaRepository<Classement, Long> {
    List<Classement> findByCompetition(String competition);
    List<Classement> findBySport(SportSection sport);
}