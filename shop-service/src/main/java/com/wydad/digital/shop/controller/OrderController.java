package com.wydad.digital.shop.controller;

import com.wydad.digital.shop.dto.OrderRequestDto;
import com.wydad.digital.shop.dto.OrderResponseDto;
import com.wydad.digital.shop.model.ShopOrder;
import com.wydad.digital.shop.repository.ShopOrderRepository;
import com.wydad.digital.shop.service.OrderPdfService;
import com.wydad.digital.shop.service.OrderService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shop/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderPdfService orderPdfService;
    private final ShopOrderRepository shopOrderRepository;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDto> createOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody OrderRequestDto dto) {
        return ResponseEntity.ok(orderService.createOrder(userEmail, userId, dto));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<OrderResponseDto>> getMyOrders(
            @RequestHeader("X-User-Email") String userEmail,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrders(userEmail, pageable));
    }

    @GetMapping("/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDto> getOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrder(userEmail, orderNumber));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponseDto>> getAllOrders(Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    /**
     * B.12 — Inventaire admin : filtres date + userEmail + productName.
     * Les filtres sont tous optionnels et cumulables. Si tout est null, on
     * renvoie les commandes paginées par date décroissante.
     */
    @GetMapping("/filter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponseDto>> filter(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String productName,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.adminFilter(
                startDate, endDate, userEmail, productName, pageable));
    }

    /**
     * ADMIN : changement de statut d'une commande (préparation, expédition,
     * livraison, annulation, remboursement). Transitions validées côté service.
     */
    @PatchMapping("/{orderNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable String orderNumber,
            @RequestBody UpdateStatusRequest body) {
        return ResponseEntity.ok(orderService.updateOrderStatus(
                orderNumber, body.status(), body.comment()));
    }

    public record UpdateStatusRequest(String status, String comment) {
    }

    /**
     * V2.2 — Facture PDF d'une commande boutique. Vue "comptable" distincte
     * du bordereau de livraison : pas de QR, lignes de facturation,
     * remises, total TTC, mentions.
     *
     * Propriétaire uniquement (ADMIN autorisé via findByOrderNumber direct,
     * à voir si on expose). La garde est centralisée via la requête par
     * (orderNumber, userEmail) — un 404 ici signifie que la commande
     * n'existe pas OU qu'elle appartient à un autre utilisateur (pas de
     * fuite d'info).
     */
    @GetMapping(value = "/{orderNumber}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadOrderInvoice(
            @PathVariable String orderNumber,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        ShopOrder order;
        if ("ADMIN".equalsIgnoreCase(role)) {
            order = shopOrderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new EntityNotFoundException("Commande introuvable : " + orderNumber));
        } else {
            order = shopOrderRepository.findByOrderNumberAndUserEmail(orderNumber, userEmail)
                    .orElseThrow(() -> new AccessDeniedException("Commande introuvable : " + orderNumber));
        }
        byte[] pdf = orderPdfService.generateInvoicePdf(order);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("facture-" + orderNumber + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}