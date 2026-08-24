package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.PlayerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerDocumentRepository extends JpaRepository<PlayerDocument, Long> {

    List<PlayerDocument> findByJoueurUserIdOrderByDateAjoutDesc(Long joueurUserId);

    /**
     * Phase 3 — médias adressés à un joueur : envois individuels
     * (joueurUserId) + envois équipe entière où il figure dans les
     * destinataires. Distincts car un envoi peut théoriquement cumuler.
     */
    @Query("""
            SELECT DISTINCT d FROM PlayerDocument d
            WHERE d.joueurUserId = :uid OR :uid IN (SELECT r FROM d.recipientUserIds r)
            ORDER BY d.dateAjout DESC
            """)
    List<PlayerDocument> findAllAddressedTo(@Param("uid") Long joueurUserId);

    /** Phase 3 — tous les médias émis par un staff (suivi côté entraîneur). */
    List<PlayerDocument> findBySenderUserIdOrderByDateAjoutDesc(Long senderUserId);
}
