package com.wydad.digital.ticket.repository;

import com.wydad.digital.ticket.enums.EventStatus;
import com.wydad.digital.ticket.enums.EventType;
import com.wydad.digital.ticket.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);
    List<Event> findByEventTypeAndStatusOrderByEventDateAsc(EventType type, EventStatus status);
    List<Event> findByEventDateBetweenOrderByEventDateAsc(LocalDateTime start, LocalDateTime end);
    List<Event> findByHomeTeamContainingIgnoreCaseOrAwayTeamContainingIgnoreCase(String home, String away);
}
