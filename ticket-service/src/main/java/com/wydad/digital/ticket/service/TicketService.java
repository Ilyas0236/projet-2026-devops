package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.dto.*;
import com.wydad.digital.ticket.enums.TicketStatus;
import com.wydad.digital.ticket.client.AuthClient;
import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.client.PaymentClient;
import com.wydad.digital.ticket.filter.UserContext;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.model.Ticket;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import com.wydad.digital.ticket.repository.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final SectionRepository sectionRepository;
    private final QrCodeService qrCodeService;
    private final NotificationClient notificationClient;
    private final PaymentClient paymentClient;
    private final AuthClient authClient;

    @Transactional
    public List<TicketResponse> purchaseTickets(PurchaseTicketRequest request) {
        // Verrous pessimistes : sérialise les achats concurrents sur le même événement/section
        Event event = eventRepository.findByIdForUpdate(request.getEventId())
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

        // Paiement
        BigDecimal total = section.getPrice().multiply(BigDecimal.valueOf(qty));
        if ("ECASH".equalsIgnoreCase(request.getPaymentMethod())) {
            paymentClient.debitEcash(
                    effectiveUserEmail,
                    total,
                    "WAC-TICKET-" + event.getId());
        } else {
            // Simulation carte bancaire
            log.info("Paiement par CARTE BANCAIRE simulé pour {} : {} DH", effectiveUserEmail, total);
        }

        // Best-effort : une panne de notification ne doit pas annuler l'achat
        notificationClient.notifyUser(
                effectiveUserId,
                effectiveUserEmail,
                "Achat confirmé",
                qty + " billet(s) pour « " + event.getTitle() + " » — merci de votre soutien !",
                "/profil/billets");

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
        // Verrou pessimiste sur la section/événement pour éviter les courses avec les achats
        Section lockedSection = null;
        Event lockedEvent = null;

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Billet non trouvé"));

        if (ticket.getStatus() == TicketStatus.USED) {
            throw new IllegalStateException("Impossible d'annuler un billet déjà utilisé");
        }
        // Empêche l'inflation du stock par annulations répétées
        if (ticket.getStatus() == TicketStatus.CANCELLED
                || ticket.getStatus() == TicketStatus.REFUNDED) {
            throw new IllegalStateException("Ce billet a déjà été annulé");
        }

        // Remboursement E-cash du billet annulé (le débit a eu lieu à l'achat).
        // Best-effort : si payment-service est indisponible, l'annulation reste
        // valide (statut CANCELLED) mais l'échec est journalisé pour retraitement.
        boolean refunded = false;
        if (ticket.getStatus() == TicketStatus.PAID && ticket.getUserEmail() != null) {
            refunded = paymentClient.refundEcash(
                    ticket.getUserEmail(),
                    ticket.getPrice(),
                    "WAC-REFUND-" + ticket.getTicketNumber());
        }

        ticket.setStatus(refunded ? TicketStatus.REFUNDED : TicketStatus.CANCELLED);
        ticket.setCancelledAt(java.time.LocalDateTime.now());

        // Restore seats (avec verrou pour cohérence avec les achats concurrents)
        if (ticket.getSection() != null) {
            lockedSection = sectionRepository.findByIdForUpdate(ticket.getSection().getId())
                    .orElse(null);
            if (lockedSection != null) {
                lockedSection.setAvailableSeats(lockedSection.getAvailableSeats() + 1);
                sectionRepository.save(lockedSection);
            }
        }

        lockedEvent = eventRepository.findByIdForUpdate(ticket.getEvent().getId())
                .orElseThrow(() -> new EntityNotFoundException("Événement du billet non trouvé"));
        lockedEvent.setAvailableSeats(lockedEvent.getAvailableSeats() + 1);
        lockedEvent.setSoldTickets(Math.max(lockedEvent.getSoldTickets() - 1, 0));
        eventRepository.save(lockedEvent);

        return mapToResponse(ticketRepository.save(ticket));
    }

    public byte[] getTicketQrCode(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Billet non trouvé"));
        return ticket.getQrCodeImage();
    }

    /**
     * B.28 — Achat sans compte (visiteur).
     *
     * 1) Crée/récupère un user VISITEUR côté auth-service (idempotent sur l'email).
     * 2) Lui rattache un billet en BASE (utilise le userId du user créé).
     * 3) Le paiement est forcément par carte (le visiteur n'a pas de e-cash).
     *
     * L'email du guest devient userEmail/userFullName sur le Ticket, comme
     * pour un membre : l'envoi du PDF et la traçabilité comptable restent
     * possibles.
     */
    @Transactional
    public List<TicketResponse> purchaseAsGuest(GuestPurchaseRequest request) {
        // 1) Création/récupération du user VISITEUR
        AuthClient.PlayerRecipient visitor = authClient.createOrFetchVisitor(
                request.getGuestEmail(),
                request.getGuestFirstName(),
                request.getGuestLastName(),
                request.getGuestPhone());
        if (visitor == null) {
            throw new IllegalStateException("Service d'authentification indisponible. Réessayez dans quelques minutes.");
        }
        Long visitorUserId = visitor.id();
        String visitorEmail = visitor.email();
        String visitorFullName = (request.getGuestFirstName() + " " + request.getGuestLastName()).trim();

        // 2) Logique d'achat classique, mais on injecte le userId du visiteur
        Event event = eventRepository.findByIdForUpdate(request.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé"));

        Section section = sectionRepository.findByEventIdAndCategory(event.getId(), request.getCategory())
                .orElseThrow(() -> new EntityNotFoundException("Section non trouvée pour cette catégorie"));

        int qty = request.getQuantity() != null ? request.getQuantity() : 1;

        if (section.getAvailableSeats() < qty) {
            throw new IllegalStateException("Pas assez de places disponibles dans cette section. Restantes: " + section.getAvailableSeats());
        }

        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < qty; i++) {
            String ticketNumber = "WAC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String qrData = "WAC-TICKET:" + ticketNumber + ":EVENT:" + event.getId() + ":USER:" + visitorUserId;

            byte[] qrImage = qrCodeService.generateQrCode(qrData);

            Ticket ticket = Ticket.builder()
                    .ticketNumber(ticketNumber)
                    .userId(visitorUserId)
                    .userFullName(visitorFullName)
                    .userEmail(visitorEmail)
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

        section.setAvailableSeats(section.getAvailableSeats() - qty);
        sectionRepository.save(section);

        event.setAvailableSeats(event.getAvailableSeats() - qty);
        event.setSoldTickets(event.getSoldTickets() + qty);
        eventRepository.save(event);

        // 3) Paiement : ECASH refusé pour un visiteur (il n'a pas de compte e-cash).
        //    Paiement par carte bancaire simulé (best-effort, log).
        BigDecimal total = section.getPrice().multiply(BigDecimal.valueOf(qty));
        if ("ECASH".equalsIgnoreCase(request.getPaymentMethod())) {
            throw new IllegalArgumentException("Le paiement par e-cash nécessite un compte membre. Veuillez payer par carte.");
        }
        log.info("Paiement CARTE (visiteur) simulé pour {} : {} DH", visitorEmail, total);

        // 4) Notification email (best-effort) avec le PDF du billet
        notificationClient.notifyUser(
                visitorUserId,
                visitorEmail,
                "Votre billet WAC",
                qty + " billet(s) pour « " + event.getTitle() + " » — merci de votre soutien !",
                "/profil/billets");

        return tickets.stream().map(this::mapToResponse).collect(Collectors.toList());
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
