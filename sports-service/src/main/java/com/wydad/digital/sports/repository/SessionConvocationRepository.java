package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.SessionConvocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SessionConvocationRepository extends JpaRepository<SessionConvocation, Long> {

    /** Toutes les convocations d'une séance, pour la vue Admin. */
    List<SessionConvocation> findBySessionId(Long sessionId);

    /** Séances où le joueur connecté est convoqué. */
    List<SessionConvocation> findByJoueurUserId(Long joueurUserId);

    boolean existsBySessionIdAndJoueurUserId(Long sessionId, Long joueurUserId);

    /** Pour la vue admin : joueurs de plusieurs séances en un seul aller-retour. */
    List<SessionConvocation> findBySessionIdIn(Collection<Long> sessionIds);

    void deleteBySessionId(Long sessionId);
}
