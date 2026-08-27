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
    private final VipTicketService vipTicketService;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .eventType(request.getEventType())
                .category(request.getCategory())
                .homeTeam(request.getHomeTeam())
                .awayTeam(request.getAwayTeam())
                .venue(request.getVenue())
                .competition(request.getCompetition())
                .adversaireLogoUrl(request.getAdversaireLogoUrl())
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

        // Phase 2 : à la création d'un match à domicile, génération automatique
        // des 4 billets VIP par joueur actif. Best-effort : ne bloque jamais
        // la création (relance manuelle possible via /internal/vip-generate).
        vipTicketService.autoGenerateIfHomeEvent(event);

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
        event.setCategory(request.getCategory());
        event.setHomeTeam(request.getHomeTeam());
        event.setAwayTeam(request.getAwayTeam());
        event.setVenue(request.getVenue());
        event.setCompetition(request.getCompetition());
        event.setAdversaireLogoUrl(request.getAdversaireLogoUrl());
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

    /**
     * Patch partiel d'une section (ADMIN). Conçu pour corriger le prix d'une
     * section SANS toucher aux billets vendus : un PUT sur l'event supprimerait
     * puis recréerait la section, ce qui violerait la FK
     * {@code tickets.section_id -> sections.id} dès qu'un billet existe.
     *
     * <p>Tous les champs du DTO sont optionnels ; seuls les non-null sont
     * appliqués. Refus de baisser la capacité en dessous du nombre de billets
     * déjà vendus (sinon, on se retrouverait avec des billets "fantômes"
     * pointant sur des places qui n'existent plus).</p>
     *
     * @throws EntityNotFoundException si la section n'existe pas
     * @throws IllegalArgumentException si le prix ≤ 0 ou la capacité incohérente
     */
    @Transactional
    public SectionResponse updateSection(Long sectionId, SectionPatchRequest req) {
        Section s = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Section " + sectionId + " introuvable"));

        if (req.getName() != null && !req.getName().isBlank()) {
            s.setName(req.getName().trim());
        }
        if (req.getCategory() != null) {
            s.setCategory(req.getCategory());
        }
        if (req.getSeatType() != null) {
            s.setSeatType(req.getSeatType());
        }
        if (req.getPrice() != null) {
            if (req.getPrice().signum() <= 0) {
                throw new IllegalArgumentException("Le prix doit être strictement positif.");
            }
            s.setPrice(req.getPrice());
        }
        if (req.getCapacity() != null) {
            int sold = (s.getCapacity() != null ? s.getCapacity() : 0)
                     - (s.getAvailableSeats() != null ? s.getAvailableSeats() : 0);
            if (req.getCapacity() < sold) {
                throw new IllegalArgumentException(
                        "Impossible de réduire la capacité à " + req.getCapacity()
                        + " : " + sold + " billet(s) déjà vendu(s) pour cette section.");
            }
            // Recale availableSeats pour conserver le même nombre de billets
            // vendus : si on augmente la capacité, on ouvre de nouvelles places ;
            // si on la baisse (au-dessus du seuil sold), idem.
            int delta = req.getCapacity() - (s.getCapacity() != null ? s.getCapacity() : 0);
            s.setCapacity(req.getCapacity());
            s.setAvailableSeats(s.getAvailableSeats() + delta);
        }

        // Pas besoin de save explicite : la section est managée par JPA dans
        // la transaction (dirty checking).
        return SectionResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .category(s.getCategory())
                .seatType(s.getSeatType())
                .capacity(s.getCapacity())
                .availableSeats(s.getAvailableSeats())
                .price(s.getPrice())
                .build();
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
                .category(event.getCategory())
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
                .adversaireLogoUrl(event.getAdversaireLogoUrl())
                .sections(sections)
                .createdAt(event.getCreatedAt())
                .build();
    }
}
