package com.wydad.digital.ticket.repository;

import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.enums.TicketStatus;
import com.wydad.digital.ticket.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Ticket> findByEventIdAndStatus(Long eventId, TicketStatus status);
    Optional<Ticket> findByTicketNumber(String ticketNumber);
    Optional<Ticket> findByQrCodeData(String qrCodeData);
    long countByEventIdAndStatus(Long eventId, TicketStatus status);
    /** V3.1 — Compte les billets d'une section pour bloquer la suppression. */
    long countBySectionId(Long sectionId);

    /** Un joueur n'a qu'une salve VIP par événement : base de l'idempotence. */
    boolean existsByEventIdAndUserIdAndCategory(Long eventId, Long userId, TicketCategory category);

    /**
     * B.12 — Inventaire admin : filtre par date + email (et eventId en option).
     * Filtre LIKE insensible à la casse sur l'email.
     */
    @Query("""
            SELECT t FROM Ticket t
              WHERE (:startDate IS NULL OR t.createdAt >= :startDate)
                AND (:endDate   IS NULL OR t.createdAt <= :endDate)
                AND (:userEmail IS NULL OR LOWER(t.userEmail) LIKE LOWER(CONCAT('%', :userEmail, '%')))
                AND (:eventId   IS NULL OR t.event.id = :eventId)
            ORDER BY t.createdAt DESC
            """)
    Page<Ticket> adminFilter(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userEmail") String userEmail,
            @Param("eventId") Long eventId,
            Pageable pageable);
}
