package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.dto.*;
import com.wydad.digital.ticket.enums.EventStatus;
import com.wydad.digital.ticket.enums.EventType;
import com.wydad.digital.ticket.enums.SeatType;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SectionRepository sectionRepository;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .eventType(request.getEventType())
                .homeTeam(request.getHomeTeam())
                .awayTeam(request.getAwayTeam())
                .venue(request.getVenue())
                .competition(request.getCompetition())
                .eventDate(request.getEventDate())
                .gateOpenTime(request.getGateOpenTime())
                .basePrice(request.getBasePrice())
                .totalCapacity(request.getTotalCapacity())
                .availableSeats(request.getTotalCapacity())
                .posterUrl(request.getPosterUrl())
                .build();

        event = eventRepository.save(event);

        if (request.getSections() != null) {
            for (SectionRequest sr : request.getSections()) {
                Section section = Section.builder()
                        .name(sr.getName())
                        .category(sr.getCategory())
                        .seatType(sr.getSeatType() != null ? sr.getSeatType() : SeatType.STANDARD)
                        .capacity(sr.getCapacity())
                        .availableSeats(sr.getCapacity())
                        .price(sr.getPrice())
                        .event(event)
                        .build();
                sectionRepository.save(section);
                event.getSections().add(section);
            }
        }

        return mapToResponse(event);
    }

    public List<EventResponse> getUpcomingEvents() {
        return eventRepository.findByStatusOrderByEventDateAsc(EventStatus.UPCOMING)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<EventResponse> getEventsByType(EventType type) {
        return eventRepository.findByEventTypeAndStatusOrderByEventDateAsc(type, EventStatus.UPCOMING)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé avec l'id: " + id));
        return mapToResponse(event);
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll()
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public EventResponse updateEventStatus(Long id, EventStatus status) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé"));
        event.setStatus(status);
        return mapToResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse updateEvent(Long id, CreateEventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé"));

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setEventType(request.getEventType());
        event.setHomeTeam(request.getHomeTeam());
        event.setAwayTeam(request.getAwayTeam());
        event.setVenue(request.getVenue());
        event.setCompetition(request.getCompetition());
        event.setEventDate(request.getEventDate());
        event.setGateOpenTime(request.getGateOpenTime());
        event.setBasePrice(request.getBasePrice());
        event.setPosterUrl(request.getPosterUrl());

        // totalCapacity : ne jamais descendre sous le nombre de places déjà vendues
        if (request.getTotalCapacity() != null) {
            int sold = event.getSoldTickets() != null ? event.getSoldTickets() : 0;
            int availableDelta = event.getAvailableSeats() - (event.getTotalCapacity() - request.getTotalCapacity());
            event.setTotalCapacity(request.getTotalCapacity());
            event.setAvailableSeats(Math.max(availableDelta, 0));
            if (request.getTotalCapacity() < sold) {
                throw new IllegalArgumentException("Capacité inférieure au nombre de billets déjà vendus (" + sold + ")");
            }
        }

        // Sections : remplacer si fournies, en conservant les ventes existantes impossible ici
        if (request.getSections() != null) {
            for (Section existing : event.getSections()) {
                sectionRepository.delete(existing);
            }
            event.getSections().clear();
            for (SectionRequest sr : request.getSections()) {
                Section section = Section.builder()
                        .name(sr.getName())
                        .category(sr.getCategory())
                        .seatType(sr.getSeatType() != null ? sr.getSeatType() : SeatType.STANDARD)
                        .capacity(sr.getCapacity())
                        .availableSeats(sr.getCapacity())
                        .price(sr.getPrice())
                        .event(event)
                        .build();
                sectionRepository.save(section);
                event.getSections().add(section);
            }
        }

        return mapToResponse(eventRepository.save(event));
    }

    @Transactional
    public void deleteEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé: " + id));

        // Protège l'historique des ventes : un événement avec des billets vendus
        // ne peut pas être supprimé (le passer en ANNULE suffit côté client).
        int sold = event.getSoldTickets() != null ? event.getSoldTickets() : 0;
        if (sold > 0) {
            throw new IllegalStateException(
                    "Impossible de supprimer cet événement : " + sold
                    + " billet(s) vendu(s). Passez son statut à ANNULE pour informer les acheteurs.");
        }

        eventRepository.delete(event);
    }

    public List<EventResponse> searchEvents(String query) {
        return eventRepository.findByHomeTeamContainingIgnoreCaseOrAwayTeamContainingIgnoreCase(query, query)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private EventResponse mapToResponse(Event event) {
        List<SectionResponse> sections = event.getSections() != null
                ? event.getSections().stream().map(s -> SectionResponse.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .category(s.getCategory())
                    .seatType(s.getSeatType())
                    .capacity(s.getCapacity())
                    .availableSeats(s.getAvailableSeats())
                    .price(s.getPrice())
                    .build()).collect(Collectors.toList())
                : List.of();

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .eventType(event.getEventType())
                .status(event.getStatus())
                .homeTeam(event.getHomeTeam())
                .awayTeam(event.getAwayTeam())
                .venue(event.getVenue())
                .competition(event.getCompetition())
                .eventDate(event.getEventDate())
                .gateOpenTime(event.getGateOpenTime())
                .basePrice(event.getBasePrice())
                .totalCapacity(event.getTotalCapacity())
                .availableSeats(event.getAvailableSeats())
                .soldTickets(event.getSoldTickets())
                .posterUrl(event.getPosterUrl())
                .sections(sections)
                .createdAt(event.getCreatedAt())
                .build();
    }
}
