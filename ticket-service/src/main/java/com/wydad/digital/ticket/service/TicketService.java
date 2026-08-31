package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.dto.*;
import com.wydad.digital.ticket.enums.TicketStatus;
import com.wydad.digital.ticket.client.AuthClient;
import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.client.PaymentClient;
import com.wydad.digital.ticket.client.SportsAcademyClient;
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
    private final SportsAcademyClient sportsAcademyClient;

    @Transactional
    public List<TicketResponse> purchaseTickets(PurchaseTicketRequest request) {
        // Verrous pessimistes : sérialise les achats concurrents sur le même événement/section
        Event event = eventRepository.findByIdForUpdate(request.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Événement non trouvé"));

        Section section = sectionRepository.findByEventIdAndCategory(event.getId(), request.getCategory())
                .orElseThrow(() -> new EntityNotFoundException("Section non trouvée pour cette catégorie"));

        // B.12 — Match EXCEPTIONNEL : si l'événement a le flag exceptional ET
        // que nous sommes dans la fenêtre des 48h précédant l'ouverture
        // publique (eventDate - 48h), seuls les ADHÉRENTS peuvent acheter.
        // L'ADMIN peut toujours passer (override) — utile pour offrir des
        // places en loge officielle.
        if (Boolean.TRUE.equals(event.getExceptional())) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime priorityOpen = event.getEventDate().minusHours(48);
            if (now.isBefore(priorityOpen) && !UserContext.isAdmin()) {
                String email = UserContext.getCurrentUserEmail();
                if (!authClient.isActiveAdherent(email)) {
                    throw new IllegalStateException(
                            "Ce match est en vente prioritaire pour les ADHÉRENTS (abonnement saisonnier)."
                                    + " Ouverture au public dans " + java.time.Duration.between(now, priorityOpen).toHours()
                                    + "h. Souscrivez un abonnement sur /abonnement pour y accéder dès maintenant.");
                }
            }
        }

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

        // B.18 — branche « PARENT achète pour son fils ». Si
        // beneficiaryAcademyMemberId est fourni, on bascule l'identité du
        // billet sur l'User shadow de l'enfant. L'IDOR (l'enfant est-il
        // bien rattaché à CE parent ?) est vérifié ici avant tout débit.
        Long beneficiaryAcademyMemberId = request.getBeneficiaryAcademyMemberId();
        String parentPayerEmail = null;
        if (beneficiaryAcademyMemberId != null) {
            // Étape 1 : lookup sports-service pour récupérer parentUserId + childFullName.
            SportsAcademyClient.AcademyMemberView child =
                    sportsAcademyClient.lookup(beneficiaryAcademyMemberId);
            if (child == null) {
                throw new IllegalStateException(
                        "Enfant académie introuvable : id=" + beneficiaryAcademyMemberId);
            }
            // Anti-IDOR : un parent ne peut acheter que pour SES enfants.
            if (!UserContext.isAdmin()
                    && !child.parentUserId().equals(UserContext.getCurrentUserId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Cet enfant n'est pas rattaché à votre compte parent");
            }
            // Étape 2 : appel auth-service pour créer/récupérer l'User shadow.
            // Le userId du billet est celui du fils, mais le parent reste
            // payeur E-Cash (effectif via effectiveUserEmail conservé = parent).
            AuthClient.EnsureChildUserResponse shadow =
                    authClient.ensureChildUser(
                            UserContext.getCurrentUserId(),
                            child.childFullName(),
                            beneficiaryAcademyMemberId);
            effectiveUserId = shadow.childUserId();
            effectiveUserEmail = shadow.email();
            parentPayerEmail = UserContext.getCurrentUserEmail();
        }

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
                    // B.18 — traçabilité de l'achat « pour enfant ».
                    .beneficiaryAcademyMemberId(beneficiaryAcademyMemberId)
                    .parentPayerEmail(parentPayerEmail)
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
     * B.12 — Inventaire admin : filtre par date + email + eventId (tous
     * optionnels et cumulables).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketResponse> adminFilter(
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            String userEmail,
            Long eventId,
            org.springframework.data.domain.Pageable pageable) {
        return ticketRepository.adminFilter(
                        startDate, endDate, userEmail, eventId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Achat sans compte SUPPRIMÉ (B.12) : tout achat requiert désormais
     * un compte VALIDE (membre WAC). Le flux B.28 « visiteur » a été
     * retiré pour respecter la règle de traçabilité (userId obligatoire).
     */

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
                // B.18 — traçabilité achat pour enfant.
                .beneficiaryAcademyMemberId(ticket.getBeneficiaryAcademyMemberId())
                .parentPayerEmail(ticket.getParentPayerEmail())
                .qrCodeData(ticket.getQrCodeData())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
