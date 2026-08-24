package com.wydad.digital.ticket.controller;

import com.wydad.digital.ticket.dto.PurchaseTicketRequest;
import com.wydad.digital.ticket.dto.TicketResponse;
import com.wydad.digital.ticket.dto.ValidateTicketRequest;
import com.wydad.digital.ticket.filter.UserContext;
import com.wydad.digital.ticket.service.TicketPdfService;
import com.wydad.digital.ticket.service.TicketService;
import com.wydad.digital.ticket.model.Ticket;
import com.wydad.digital.ticket.repository.TicketRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ticket/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketPdfService ticketPdfService;
    private final TicketRepository ticketRepository;

    @PostMapping("/purchase")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('JOUEUR') or hasRole('ADMIN')")
    public ResponseEntity<List<TicketResponse>> purchaseTickets(@Valid @RequestBody PurchaseTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.purchaseTickets(request));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('JOUEUR') or hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<List<TicketResponse>> getTicketsByUser(@PathVariable Long userId) {
        if (!UserContext.isAdmin() && !userId.equals(UserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Accès aux billets d'un autre utilisateur interdit");
        }
        return ResponseEntity.ok(ticketService.getTicketsByUser(userId));
    }

    @GetMapping("/number/{ticketNumber}")
    public ResponseEntity<TicketResponse> getTicketByNumber(@PathVariable String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Billet non trouvé: " + ticketNumber));
        assertOwnerOrAdmin(ticket.getUserId());
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
        assertOwnerOrAdmin(ticket.getUserId());
        return ResponseEntity.ok(ticketService.cancelTicket(ticketId));
    }

    @GetMapping("/{ticketId}/qr")
    public ResponseEntity<byte[]> getTicketQrCode(@PathVariable Long ticketId) {
        assertOwnership(ticketId);
        byte[] qrCode = ticketService.getTicketQrCode(ticketId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(qrCode, headers, HttpStatus.OK);
    }

    @GetMapping("/{ticketId}/pdf")
    public ResponseEntity<byte[]> downloadTicketPdf(@PathVariable Long ticketId) {
        assertOwnership(ticketId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Billet non trouvé"));
        byte[] pdf = ticketPdfService.generateTicketPdf(ticket);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("billet-" + ticket.getTicketNumber() + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /** Un utilisateur ne peut accéder qu'à ses billets ; ADMIN autorisé. */
    private void assertOwnership(Long ticketOwnerId) {
        if (!UserContext.isAdmin() && !ticketOwnerId.equals(UserContext.getCurrentUserId())) {
            throw new AccessDeniedException("Ce billet n'appartient pas à l'utilisateur connecté");
        }
    }

    private void assertOwnerOrAdmin(Long ownerId) {
        assertOwnership(ownerId);
    }
}
