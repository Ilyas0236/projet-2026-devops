package com.wydad.digital.ticket.controller;

import com.wydad.digital.ticket.dto.PurchaseTicketRequest;
import com.wydad.digital.ticket.dto.TicketResponse;
import com.wydad.digital.ticket.dto.ValidateTicketRequest;
import com.wydad.digital.ticket.filter.UserContext;
import com.wydad.digital.ticket.service.TicketPdfService;
import com.wydad.digital.ticket.service.TicketService;
import com.wydad.digital.ticket.service.VipTicketService;
import com.wydad.digital.ticket.model.Ticket;
import com.wydad.digital.ticket.repository.TicketRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ticket/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketPdfService ticketPdfService;
    private final TicketRepository ticketRepository;
    private final VipTicketService vipTicketService;

    @PostMapping("/purchase")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('PARENT') or hasRole('ADMIN')")
    public ResponseEntity<List<TicketResponse>> purchaseTickets(@Valid @RequestBody PurchaseTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.purchaseTickets(request));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADHERENT','JOUEUR','STAFF','ENTRAINEUR','JOURNALISTE','PRESIDENT','PARENT','ADMIN')")
    public ResponseEntity<List<TicketResponse>> getTicketsByUser(@PathVariable Long userId) {
        if (!UserContext.isAdmin() && !userId.equals(UserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Accès aux billets d'un autre utilisateur interdit");
        }
        return ResponseEntity.ok(ticketService.getTicketsByUser(userId));
    }

    /**
     * B.12 — Inventaire admin des billets (filtres date + email + eventId).
     * Tous les filtres sont optionnels et cumulables.
     */
    @GetMapping("/admin/filter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.data.domain.Page<TicketResponse>> adminFilter(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime startDate,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime endDate,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) Long eventId,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ticketService.adminFilter(
                startDate, endDate, userEmail, eventId, pageable));
    }

    /**
     * B.29 — Distribution bulk de 4 billets VIP par l'ADMIN à tous les
     * membres SENIOR (JOUEUR + STAFF + ENTRAINEUR) du groupe
     * discipline+catégorie de l'événement. Idempotent : peut être
     * ré-appelé sans créer de doublon.
     *
     * <p>L'auto-trigger sports-service utilise l'endpoint interne
     * {@code /internal/vip-generate} (gateway-bloqué) ; ce nouvel
     * endpoint ADMIN public permet de relancer manuellement depuis
     * l'UI billetterie (par exemple après ajout d'un membre, ou si la
     * première génération a échoué pour cause de section VIP absente).</p>
     */
    @PostMapping("/events/{eventId}/vip-distribute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> vipDistribute(@PathVariable Long eventId) {
        try {
            VipTicketService.VipGenerationResult result =
                    vipTicketService.generateVipTicketsForEvent(eventId);
            // B.29 — on expose les compteurs avec une sémantique
            // « bénéficiaires » (inclut STAFF/ENTRAINEUR) plutôt que
            // « joueurs ». Le record interne garde joueursServis par
            // rétro-compat avec l'endpoint interne /vip-generate.
            return ResponseEntity.ok(Map.of(
                    "beneficiairesServis", result.joueursServis(),
                    "billetsCrees", result.billetsCrees()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("erreur", e.getMessage()));
        }
    }

    @GetMapping("/number/{ticketNumber}")
    public ResponseEntity<TicketResponse> getTicketByNumber(@PathVariable String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Billet non trouvé: " + ticketNumber));
        assertOwnerOrAdmin(ticket);
        return ResponseEntity.ok(ticketService.mapToResponsePublic(ticket));
    }

    @PostMapping("/validate")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<TicketResponse> validateTicket(@Valid @RequestBody ValidateTicketRequest request) {
        return ResponseEntity.ok(ticketService.validateTicket(request.getQrCodeData()));
    }

    @PostMapping("/{ticketId}/cancel")
    public ResponseEntity<TicketResponse> cancelTicket(@PathVariable Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Billet non trouvé"));
        assertOwnerOrAdmin(ticket);
        return ResponseEntity.ok(ticketService.cancelTicket(ticketId));
    }

    @GetMapping("/{ticketId}/qr")
    public ResponseEntity<byte[]> getTicketQrCode(@PathVariable Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Billet non trouvé"));
        assertOwnership(ticket);
        byte[] qrCode = ticketService.getTicketQrCode(ticketId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(qrCode, headers, HttpStatus.OK);
    }

    @GetMapping("/{ticketId}/pdf")
    public ResponseEntity<byte[]> downloadTicketPdf(@PathVariable Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Billet non trouvé"));
        // Ownership sur le PROPRIETAIRE du billet (bug : on comparait
        // l'id du billet lui-meme -> 403 pour tout utilisateur non admin).
        assertOwnership(ticket);
        byte[] pdf = ticketPdfService.generateTicketPdf(ticket);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("billet-" + ticket.getTicketNumber() + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * V2.2 — Facture PDF du billet (vue "comptable" : pas de QR, ligne
     * unique, totaux, mentions). Même protection d'ownership que /pdf.
     */
    @GetMapping("/{ticketId}/invoice")
    public ResponseEntity<byte[]> downloadTicketInvoice(@PathVariable Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Billet non trouvé"));
        assertOwnership(ticket);
        byte[] pdf = ticketPdfService.generateInvoicePdf(ticket);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("facture-" + ticket.getTicketNumber() + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * Un utilisateur ne peut accéder qu'à ses billets ; ADMIN autorisé.
     *
     * <p>B.18 — exception pour les billets achetés par un parent pour
     * son enfant : le parent payeur (identifié par
     * {@code ticket.parentPayerEmail}) peut accéder au PDF/QR/invoice
     * du billet de son fils, sans quoi il ne pourrait pas lui présenter
     * le billet à l'entrée du stade. Le contrôle se fait sur l'email
     * du parent payeur (toujours présent si achat « pour enfant »).</p>
     */
    private void assertOwnership(Ticket ticket) {
        if (UserContext.isAdmin() || ticket.getUserId().equals(UserContext.getCurrentUserId())) {
            return;
        }
        if (ticket.getParentPayerEmail() != null
                && ticket.getParentPayerEmail().equalsIgnoreCase(UserContext.getCurrentUserEmail())) {
            return;
        }
        throw new AccessDeniedException("Ce billet n'appartient pas à l'utilisateur connecté");
    }

    private void assertOwnerOrAdmin(Ticket ticket) {
        assertOwnership(ticket);
    }
}
