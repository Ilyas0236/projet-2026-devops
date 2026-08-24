package com.wydad.digital.ticket.controller;

import com.wydad.digital.ticket.config.InternalSecretValidator;
import com.wydad.digital.ticket.service.VipTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints internes service-à-service du ticket-service (Phase 2).
 * Protégés par X-Internal-Secret ; la gateway bloque /api/ticket/internal/**
 * en amont. Appelé par content-service/sports-service à la création d'un
 * match à domicile, ou par un ADMIN pour une relance manuelle.
 */
@RestController
@RequestMapping("/api/ticket/internal")
@RequiredArgsConstructor
public class InternalTicketController {

    private final VipTicketService vipTicketService;
    private final InternalSecretValidator secretValidator;

    /**
     * Génère les 4 billets VIP par joueur actif pour l'événement donné.
     * Idempotent : peut être rappelé sans créer de doublon.
     */
    @PostMapping("/vip-generate/{eventId}")
    public ResponseEntity<?> generateVip(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable Long eventId) {
        if (!secretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            var result = vipTicketService.generateVipTicketsForEvent(eventId);
            return ResponseEntity.ok(Map.of(
                    "joueursServis", result.joueursServis(),
                    "billetsCrees", result.billetsCrees()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Match extérieur ou section VIP absente : rejet métier explicite
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("erreur", e.getMessage()));
        }
    }
}
