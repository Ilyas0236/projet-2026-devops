package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.dto.*;
import com.wydad.digital.ticket.enums.TicketStatus;
import com.wydad.digital.ticket.filter.UserContext;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.model.Ticket;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import com.wydad.digital.ticket.repository.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final SectionRepository sectionRepository;
    private final QrCodeService qrCodeService;

    @Transactional
    public List<TicketResponse> purchaseTickets(PurchaseTicketRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé"));

        Section section = sectionRepository.findByEventIdAndCategory(event.getId(), request.getCategory())
                .orElseThrow(() -> new EntityNotFoundException("Section non trouvée pour cette catégorie"));

        int qty = request.getQuantity() != null ? request.getQuantity() : 1;

        if (section.getAvailableSeats() < qty) {
            throw new IllegalStateException("Pas assez de places disponibles dans cette section. Restantes: " + section.getAvailableSeats());
        }

        List<Ticket> tickets = new ArrayList<>();
        // Identité depuis le contexte (JWT) : un utilisateur ne peut acheter que pour lui-même
        Long effectiveUserId = UserContext.isAdmin() && request.getUserId() != null
                ? request.getUserId()
                : UserContext.getCurrentUserId();
        String effectiveUserEmail = UserContext.isAdmin() && request.getUserEmail() != null
                ? request.getUserEmail()
                : UserContext.getCurrentUserEmail();

        for (int i = 0; i < qty; i++) {
            String ticketNumber = "WAC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String qrData = "WAC-TICKET:" + ticketNumber + ":EVENT:" + event.getId() + ":USER:" + effectiveUserId;

            byte[] qrImage = qrCodeService.generateQrCode(qrData);

            Ticket ticket = Ticket.builder()
                    .ticketNumber(ticketNumber)
                    .userId(effectiveUserId)
                    .userFullName(request.getUserFullName())
                    .userEmail(effectiveUserEmail)
                    .event(event)
                    .section(section)
                    .category(request.getCategory())
                    .price(section.getPrice())
                    .qrCodeData(qrData)
                    .qrCodeImage(qrImage)
                    .status(TicketStatus.PAID)
                    .build();

            tickets.add(ticketRepository.save(ticket));
        }

        // Update available seats
        section.setAvailableSeats(section.getAvailableSeats() - qty);
        sectionRepository.save(section);

        event.setAvailableSeats(event.getAvailableSeats() - qty);
        event.setSoldTickets(event.getSoldTickets() + qty);
        eventRepository.save(event);

        return tickets.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<TicketResponse> getTicketsByUser(Long userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public TicketResponse getTicketByNumber(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new EntityNotFoundException("Billet non trouvé: " + ticketNumber));
        return mapToResponse(ticket);
    }

    @Transactional
    public TicketResponse validateTicket(String qrCodeData) {
        Ticket ticket = ticketRepository.findByQrCodeData(qrCodeData)
                .orElseThrow(() -> new EntityNotFoundException("Billet non trouvé pour ce QR code"));

        if (ticket.getStatus() == TicketStatus.USED) {
            throw new IllegalStateException("Ce billet a déjà été utilisé");
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new IllegalStateException("Ce billet a été annulé");
        }
        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new IllegalStateException("Ce billet n'est pas valide. Statut: " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.USED);
        ticket.setValidatedAt(java.time.LocalDateTime.now());
        return mapToResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Billet non trouvé"));

        if (ticket.getStatus() == TicketStatus.USED) {
            throw new IllegalStateException("Impossible d'annuler un billet déjà utilisé");
        }
        // Empêche l'inflation du stock par annulations répétées
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new IllegalStateException("Ce billet a déjà été annulé");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setCancelledAt(java.time.LocalDateTime.now());

        // Restore seats
        Section section = ticket.getSection();
        if (section != null) {
            section.setAvailableSeats(section.getAvailableSeats() + 1);
            sectionRepository.save(section);
        }

        Event event = ticket.getEvent();
        event.setAvailableSeats(event.getAvailableSeats() + 1);
        event.setSoldTickets(event.getSoldTickets() - 1);
        eventRepository.save(event);

        return mapToResponse(ticketRepository.save(ticket));
    }

    public byte[] getTicketQrCode(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Billet non trouvé"));
        return ticket.getQrCodeImage();
    }

    /** Version publique du mapper pour les contrôleurs. */
    public TicketResponse mapToResponsePublic(Ticket ticket) {
        return mapToResponse(ticket);
    }

    private TicketResponse mapToResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .userId(ticket.getUserId())
                .userFullName(ticket.getUserFullName())
                .eventId(ticket.getEvent().getId())
                .eventTitle(ticket.getEvent().getTitle())
                .eventDate(ticket.getEvent().getEventDate())
                .venue(ticket.getEvent().getVenue())
                .category(ticket.getCategory())
                .sectionName(ticket.getSection() != null ? ticket.getSection().getName() : null)
                .seatNumber(ticket.getSeatNumber())
                .status(ticket.getStatus())
                .price(ticket.getPrice())
                .qrCodeData(ticket.getQrCodeData())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
