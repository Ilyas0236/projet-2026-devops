package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.PlayerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerDocumentRepository extends JpaRepository<PlayerDocument, Long> {

    List<PlayerDocument> findByJoueurUserIdOrderByDateAjoutDesc(Long joueurUserId);
}
