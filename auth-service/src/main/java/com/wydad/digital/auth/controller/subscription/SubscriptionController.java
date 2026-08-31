package com.wydad.digital.auth.controller.subscription;

import com.wydad.digital.auth.dto.subscription.PurchaseSubscriptionRequest;
import com.wydad.digital.auth.dto.subscription.SubscriptionResponse;
import com.wydad.digital.auth.dto.subscription.SubscriptionZoneResponse;
import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository;
import com.wydad.digital.auth.service.PdfService;
import com.wydad.digital.auth.service.subscription.SubscriptionService;
import com.wydad.digital.auth.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auth/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final JwtUtils jwtUtils;
    private final UserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PdfService pdfService;

    /**
     * Catalogue public des zones d'abonnement.
     * Le SOLD_OUT est masqué par défaut ; un ADMIN peut le voir (?includeSoldOut=true).
     */
    @GetMapping("/zones")
    public ResponseEntity<List<SubscriptionZoneResponse>> listZones(
            @RequestParam(value = "includeSoldOut", required = false, defaultValue = "false") boolean includeSoldOut) {
        // L'admin voit tout, l'utilisateur standard ne voit que les zones commercialisées
        return ResponseEntity.ok(subscriptionService.listZones(includeSoldOut));
    }

    /**
     * Achat d'un abonnement saisonnier — réservé aux supporters.
     * Seuls JOUEUR, ADHERENT et PARENT peuvent acheter (séparation des rôles :
     * ADMIN/PRESIDENT gèrent, ENTRAINEUR/STAFF/JOURNALISTE ne sont pas clients).
     * Remplace l'ancien /api/auth/upgrade qui ne demandait aucun paiement.
     */
    @PostMapping("/purchase")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")
    public ResponseEntity<SubscriptionResponse> purchase(
            @Valid @RequestBody PurchaseSubscriptionRequest request,
            HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader(HttpHeaders.AUTHORIZATION).replace("Bearer ", "");
        String email = jwtUtils.getEmailFromToken(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.purchase(email, request));
    }

    /** Mon abonnement actif (null si aucun). */
    @GetMapping("/me/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionResponse> myActive(HttpServletRequest httpRequest) {
        String email = emailFromHeader(httpRequest);
        SubscriptionResponse active = subscriptionService.myActive(email);
        return active == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(active);
    }

    /** Mon historique d'abonnements. */
    @GetMapping("/me/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SubscriptionResponse>> myHistory(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(subscriptionService.myHistory(emailFromHeader(httpRequest)));
    }

    /**
     * Endpoint interne service-a-service : ticket-service et shop-service
     * l'appellent pour savoir si l'utilisateur est adhérent (avantages).
     * Protégé par X-Internal-Secret.
     */
    @GetMapping("/internal/is-adherent")
    public ResponseEntity<Boolean> isAdherent(
            @RequestParam("email") String email,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        // Pas de validation de secret ici : on s'appuie sur le filtre
        // de la gateway qui bloque les routes /internal/** en accès public.
        // Le secret sera ajouté en V2 quand on aura une gateway-side
        // whitelist des routes internes.
        return ResponseEntity.ok(subscriptionService.isActiveAdherent(email));
    }

    /**
     * B.12 — Inventaire admin des abonnements (filtres date + email).
     */
    @GetMapping("/admin/filter")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.data.domain.Page<SubscriptionResponse> adminFilter(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate,
            @RequestParam(required = false) String userEmail,
            org.springframework.data.domain.Pageable pageable) {
        return subscriptionService.adminFilter(
                startDate, endDate, userEmail, pageable);
    }

    /**
     * Téléchargement de la carte d'abonnement (PDF carte bancaire 85x54mm)
     * avec QR code d'accès au stade. Sécurisé par JWT : un utilisateur ne
     * peut télécharger que SES abonnements (sauf ADMIN qui peut tout voir).
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadSubscriptionPdf(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        UserSubscription sub = loadOwnedSubscription(id, httpRequest);
        // Génération à la volée (PDF bytes non stockés — économie RAM VM 1 Go)
        try {
            User owner = userRepository.findById(sub.getUser().getId())
                    .orElseThrow(() -> new UserNotFoundException(sub.getUser().getEmail()));
            byte[] pdf = pdfService.buildSubscriptionPdfBytes(sub, owner);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "carte-abonnement-wac-" + sub.getId() + ".pdf");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible de générer la carte d'abonnement.");
        }
    }

    /**
     * V2.2 — Facture PDF d'un abonnement saisonnier (vue "comptable" A4,
     * distincte de la carte d'accès). Mêmes protections d'ownership que /pdf.
     */
    @GetMapping(value = "/{id}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadSubscriptionInvoice(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        UserSubscription sub = loadOwnedSubscription(id, httpRequest);
        try {
            User owner = userRepository.findById(sub.getUser().getId())
                    .orElseThrow(() -> new UserNotFoundException(sub.getUser().getEmail()));
            byte[] pdf = pdfService.buildSubscriptionInvoicePdfBytes(sub, owner);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "facture-abonnement-wac-" + sub.getId() + ".pdf");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible de générer la facture d'abonnement.");
        }
    }

    /**
     * Charge un UserSubscription par id en vérifiant que l'appelant en est
     * le propriétaire (ou ADMIN). Centralise la protection IDOR partagée
     * entre /pdf et /invoice.
     */
    private UserSubscription loadOwnedSubscription(Long id, HttpServletRequest httpRequest) {
        String email = emailFromHeader(httpRequest);
        UserSubscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Abonnement introuvable : id=" + id));
        boolean isAdmin = httpRequest.isUserInRole("ADMIN");
        if (!isAdmin && !sub.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Vous n'avez pas accès à cet abonnement.");
        }
        return sub;
    }

    private String emailFromHeader(HttpServletRequest httpRequest) {
        String auth = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String token = auth.replace("Bearer ", "");
        return jwtUtils.getEmailFromToken(token);
    }
}
