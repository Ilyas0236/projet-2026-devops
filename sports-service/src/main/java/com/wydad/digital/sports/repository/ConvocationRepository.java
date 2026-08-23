package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.Convocation;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConvocationRepository extends JpaRepository<Convocation, Long> {

    List<Convocation> findByJoueurUserIdOrderBySession_SessionDateAsc(Long joueurUserId);

    List<Convocation> findBySession_CategoryAndSession_SportTypeOrderByCreatedAtDesc(
            Category category, SportType sportType);

    boolean existsByJoueurUserIdAndSession_Id(Long joueurUserId, Long sessionId);

    /** Historique de présence : réponses déjà données, plus récentes d'abord. */
    List<Convocation> findByJoueurUserIdAndResponseStatusIsNotNullOrderByRespondedAtDesc(
            Long joueurUserId);
}
