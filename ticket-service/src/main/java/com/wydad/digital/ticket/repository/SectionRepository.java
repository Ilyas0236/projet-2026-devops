package com.wydad.digital.ticket.repository;

import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByEventId(Long eventId);
    Optional<Section> findByEventIdAndCategory(Long eventId, TicketCategory category);
}
