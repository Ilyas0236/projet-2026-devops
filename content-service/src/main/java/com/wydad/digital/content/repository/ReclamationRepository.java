package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.Reclamation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {

    List<Reclamation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Reclamation> findAllByOrderByCreatedAtDesc();
}
