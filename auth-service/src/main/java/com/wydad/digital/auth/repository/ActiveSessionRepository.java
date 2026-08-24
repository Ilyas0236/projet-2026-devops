package com.wydad.digital.auth.repository;

import com.wydad.digital.auth.model.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveSessionRepository extends JpaRepository<ActiveSession, Long> {
    List<ActiveSession> findByEmailAndRevokedFalse(String email);
    boolean existsByEmailAndRevokedFalse(String email);
    Optional<ActiveSession> findByTokenAndRevokedFalse(String token);
    void deleteByEmail(String email);
}