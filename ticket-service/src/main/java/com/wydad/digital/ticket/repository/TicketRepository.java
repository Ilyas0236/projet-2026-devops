package com.wydad.digital.ticket.repository;

import com.wydad.digital.ticket.enums.TicketStatus;
import com.wydad.digital.ticket.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Ticket> findByEventIdAndStatus(Long eventId, TicketStatus status);
    Optional<Ticket> findByTicketNumber(String ticketNumber);
    Optional<Ticket> findByQrCodeData(String qrCodeData);
    long countByEventIdAndStatus(Long eventId, TicketStatus status);
}
