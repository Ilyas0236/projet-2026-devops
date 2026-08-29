package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.client.AuthClient;
import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.dto.*;
import com.wydad.digital.ticket.enums.EventStatus;
import com.wydad.digital.ticket.enums.EventType;
import com.wydad.digital.ticket.enums.SeatType;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import com.wydad.digital.ticket.repository.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SectionRepository sectionRepository;
    private final TicketRepository ticketRepository;
    private final VipTicketService vipTicketService;
    private final NotificationClient notificationClient;
    private final AuthClient authClient;

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
                // V1.1 — FK logique vers le match de calendrier (content-service).
                // On ne valide pas l'existence ici : l'admin saisit un id qu'il
                // a copié/choisi dans le sélecteur, et un match supprimé laisse
                // juste la valeur orpheline (dégradation douce, pas de 500).
                .matchId(request.getMatchId())
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

        // E.1 — Notification supporters : si le match est dans les 30 jours
        // (donc pertinent pour l'achat de billets), on broadcast IN_APP à
        // tous les supporters actifs (USER/ADHERENT). Best-effort : ne
        // bloque jamais la création de l'événement.
        notifySupportersIfRelevant(event);

        return mapToResponse(event);
    }

    /**
     * E.1 — Broadcast IN_APP supporters si l'event est dans la fenêtre
     * 30 jours (J-30 → J+0). En-deçà, on attend — un match dans 6 mois
     * sera notifié par un futur scheduler. Au-delà, on ne spamme pas.
     *
     * <p>Deux étapes : (1) vérifier qu'il y a au moins un supporter actif ;
     * (2) appeler le broadcast ciblé de notification-service avec la liste
     * d'IDs supporters (USER/ADHERENT) — pas d'admin/-président/joueur,
     * qui reçoivent leurs propres canaux. Toute erreur est journalisée et
     * ignorée (best-effort).</p>
     */
    private void notifySupportersIfRelevant(Event event) {
        try {
            if (event.getEventDate() == null) return;
            LocalDateTime now = LocalDateTime.now();
            long daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), event.getEventDate().toLocalDate());
            if (daysUntil < 0 || daysUntil > 30) {
                log.debug("Event {} : date hors fenêtre 30j (J-{}), pas de notification supporters",
                        event.getId(), daysUntil);
                return;
            }
            var supporters = authClient.fetchActiveSupporters();
            if (supporters.isEmpty()) {
                log.info("Event {} : aucun supporter actif à notifier", event.getId());
                return;
            }
            java.util.List<Long> supporterIds = supporters.stream()
                    .map(AuthClient.PlayerRecipient::id)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (supporterIds.isEmpty()) {
                log.info("Event {} : supporters sans id (anomalie), broadcast ignoré", event.getId());
                return;
            }
            String title = "Nouveau match : " + event.getTitle();
            String homeTeam = event.getHomeTeam() != null ? event.getHomeTeam() : "WAC";
            String awayTeam = event.getAwayTeam() != null ? " vs " + event.getAwayTeam() : "";
            String venue = event.getVenue() != null ? " @ " + event.getVenue() : "";
            String message = homeTeam + awayTeam + venue
                    + " le " + event.getEventDate().toLocalDate()
                    + " — billets disponibles sur le site.";
            notificationClient.notifyBroadcastTargeted(supporterIds, title, message, "/billetterie/" + event.getId());
            log.info("Event {} : notification supporters ciblée envoyée ({} destinataires)",
                    event.getId(), supporterIds.size());
        } catch (Exception e) {
            log.warn("Event {} : notification supporters échouée : {}",
                    event.getId(), e.getMessage());
        }
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

    /**
     * B.12 — Bascule le flag EXCEPTIONNEL sans toucher au reste.
     * Utilisé par l'admin pour marquer un match LDC/quart/semi/finale.
     */
    @Transactional
    public EventResponse setExceptional(Long id, Boolean value) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé"));
        event.setExceptional(Boolean.TRUE.equals(value));
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
        // B.12 — match EXCEPTIONNEL : ne pas écraser si non fourni (null = "no change")
        if (request.getExceptional() != null) {
            event.setExceptional(request.getExceptional());
        }
        // V1.1 — matchId : un null explicite détache l'événement du match,
        // un id non-null le ré-adosse. Si le champ est absent du payload
        // (champ JSON omis), on conserve l'identique (cohérence avec le
        // pattern PUT des autres champs optionnels).
        // Note : on autorise setMatchId(null) pour permettre un "détachement".
        event.setMatchId(request.getMatchId());

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

    /**
     * V3.1 — Crée une section sur un événement EXISTANT (ADMIN).
     * Utile pour ajouter une catégorie de billets a posteriori (ex. : ouvrir
     * une section « VIRAGE » pour un match déjà planifié).
     *
     * <p>Si l'événement a déjà une section de la même catégorie, le service
     * renvoie 409 via IllegalStateException — l'admin doit d'abord
     * supprimer ou modifier la section existante.</p>
     */
    @Transactional
    public SectionResponse createSection(Long eventId, SectionRequest req) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Événement " + eventId + " introuvable"));
        if (sectionRepository.findByEventIdAndCategory(eventId, req.getCategory()).isPresent()) {
            throw new IllegalStateException(
                    "L'événement a déjà une section " + req.getCategory()
                    + " — modifiez ou supprimez l'existante avant d'en créer une nouvelle.");
        }
        Section section = Section.builder()
                .name(req.getName().trim())
                .category(req.getCategory())
                .seatType(req.getSeatType() != null ? req.getSeatType() : SeatType.STANDARD)
                .capacity(req.getCapacity())
                .availableSeats(req.getCapacity())
                .price(req.getPrice())
                .event(event)
                .build();
        section = sectionRepository.save(section);
        // Recalcule le totalCapacity de l'événement = somme des capacités.
        event.setTotalCapacity(event.getSections().stream()
                .mapToInt(sec -> sec.getCapacity() != null ? sec.getCapacity() : 0).sum());
        event.setAvailableSeats(event.getSections().stream()
                .mapToInt(sec -> sec.getAvailableSeats() != null ? sec.getAvailableSeats() : 0).sum());
        eventRepository.save(event);
        return SectionResponse.builder()
                .id(section.getId())
                .name(section.getName())
                .category(section.getCategory())
                .seatType(section.getSeatType())
                .capacity(section.getCapacity())
                .availableSeats(section.getAvailableSeats())
                .price(section.getPrice())
                .build();
    }

    /**
     * V3.1 — Supprime une section (ADMIN).
     *
     * <p>Refus si la section a déjà des billets vendus (FK {@code tickets.
     * section_id} + intégrité historique). On ne fait pas de soft-delete :
     * un admin peut explicitement supprimer une section vide via l'UI.</p>
     */
    @Transactional
    public void deleteSection(Long sectionId) {
        Section s = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Section " + sectionId + " introuvable"));
        long sold = ticketRepository.countBySectionId(sectionId);
        if (sold > 0) {
            throw new IllegalStateException(
                    "Impossible de supprimer la section : " + sold
                    + " billet(s) y sont rattachés. Annulez d'abord les billets ou modifiez la capacité à 0.");
        }
        Event event = s.getEvent();
        sectionRepository.delete(s);
        // Recalcule les totaux de l'événement après suppression.
        if (event != null) {
            int total = event.getSections().stream()
                    .mapToInt(sec -> sec.getCapacity() != null ? sec.getCapacity() : 0).sum();
            int available = event.getSections().stream()
                    .mapToInt(sec -> sec.getAvailableSeats() != null ? sec.getAvailableSeats() : 0).sum();
            event.setTotalCapacity(total);
            event.setAvailableSeats(available);
            eventRepository.save(event);
        }
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
                .exceptional(event.getExceptional() != null && event.getExceptional())
                .basePrice(event.getBasePrice())
                .totalCapacity(event.getTotalCapacity())
                .availableSeats(event.getAvailableSeats())
                .soldTickets(event.getSoldTickets())
                .posterUrl(event.getPosterUrl())
                .adversaireLogoUrl(event.getAdversaireLogoUrl())
                .sections(sections)
                .createdAt(event.getCreatedAt())
                .matchId(event.getMatchId())
                .build();
    }
}
