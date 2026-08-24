package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.client.AuthClient;
import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.dto.TicketResponse;
import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.enums.TicketStatus;
import com.wydad.digital.ticket.model.Event;
import com.wydad.digital.ticket.model.Section;
import com.wydad.digital.ticket.model.Ticket;
import com.wydad.digital.ticket.repository.EventRepository;
import com.wydad.digital.ticket.repository.SectionRepository;
import com.wydad.digital.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 2 — Génération automatique des billets VIP joueurs.
 *
 * À chaque match du Wydad À DOMICILE, chaque joueur actif reçoit 4 billets
 * VIP gratuits (invités du club), attachés à son compte et téléchargeables
 * en PDF avec QR unique depuis son espace.
 *
 * Règles métier :
 * - Match à l'extérieur → aucun billet (rejet explicite).
 * - Idempotent : relancer la génération pour un événement déjà traité ne
 *   crée AUCUN doublon — seuls les joueurs manquants sont servis.
 * - Hors circuit de vente : prix 0, statut PAID directement (offre du club),
 *   PAS de débit E-cash — ces billets ne passent jamais par PaymentClient.
 * - Les places VIP ne touchent pas au compteur public availableSeats :
 *   une section VIP dédiée porte la capacité d'accueil des invités.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipTicketService {

    /** Nombre de billets VIP offerts par joueur actif à domicile. */
    static final int BILLETS_PAR_JOUEUR = 4;

    private final EventRepository eventRepository;
    private final SectionRepository sectionRepository;
    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final AuthClient authClient;
    private final NotificationClient notificationClient;

    /**
     * Résultat d'une génération : compteurs pour journalisation et tests.
     */
    public record VipGenerationResult(int joueursServis, int billetsCrees) {}

    /**
     * Déclencheur automatique après création d'un événement : génère les
     * billets VIP si c'est un match à domicile. Best-effort et silencieux :
     * une erreur ici ne DOIT JAMAIS faire échouer la création de l'événement
     * (relançable via /internal/vip-generate, génération idempotente).
     */
    @Transactional
    public void autoGenerateIfHomeEvent(Event event) {
        try {
            if (!isHomeMatch(event)) {
                return;
            }
            VipGenerationResult result = generateVipTicketsForEvent(event.getId());
            log.info("Auto-génération VIP après création de l'événement {} : {} joueur(s), {} billet(s)",
                    event.getId(), result.joueursServis(), result.billetsCrees());
        } catch (Exception e) {
            // Section VIP absente, auth-service injoignable... : l'ADMIN peut
            // relancer manuellement via POST /api/ticket/internal/vip-generate/{eventId}.
            log.warn("Auto-génération VIP non aboutie pour l'événement {} — relance manuelle possible",
                    event.getId(), e);
        }
    }

    @Transactional
    public VipGenerationResult generateVipTicketsForEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Événement non trouvé: " + eventId));

        if (!isHomeMatch(event)) {
            throw new IllegalStateException(
                    "Aucun billet VIP : « " + event.getTitle() + " » est un match à l'extérieur");
        }

        Section sectionVip = sectionRepository.findByEventIdAndCategory(eventId, TicketCategory.VIP)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune section VIP sur cet événement — créez-la avant la génération"));

        var players = authClient.fetchActivePlayers();
        int billets = 0;

        for (var player : players) {
            if (ticketRepository.existsByEventIdAndUserIdAndCategory(eventId, player.id(), TicketCategory.VIP)) {
                continue; // déjà servi (relance/idempotence)
            }
            for (int i = 0; i < BILLETS_PAR_JOUEUR; i++) {
                String ticketNumber = "WAC-VIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String qrData = "WAC-TICKET:" + ticketNumber + ":EVENT:" + event.getId() + ":USER:" + player.id();
                ticketRepository.save(Ticket.builder()
                        .ticketNumber(ticketNumber)
                        .userId(player.id())
                        .userFullName(player.displayName())
                        .userEmail(player.email())
                        .event(event)
                        .section(sectionVip)
                        .category(TicketCategory.VIP)
                        // Offre du club : gratuit, déjà payé, hors circuit E-cash.
                        .price(BigDecimal.ZERO)
                        .qrCodeData(qrData)
                        .qrCodeImage(qrCodeService.generateQrCode(qrData))
                        .status(TicketStatus.PAID)
                        .build());
            }
            sectionVip.setAvailableSeats(Math.max(sectionVip.getAvailableSeats() - BILLETS_PAR_JOUEUR, 0));
            billets += BILLETS_PAR_JOUEUR;
        }

        sectionRepository.save(sectionVip);

        // Best-effort : prévenir chaque joueur nouvellement servi.
        notifyPlayers(players, eventId, billets);

        log.info("Génération VIP événement {} : {} joueur(s), {} billet(s)",
                eventId, players.size(), billets);
        return new VipGenerationResult(players.size(), billets);
    }

    /** Un match est « à domicile » si le Wydad est l'équipe recevante. */
    private boolean isHomeMatch(Event event) {
        return event.getHomeTeam() != null && !event.getHomeTeam().isBlank()
                && event.getAwayTeam() != null && !event.getAwayTeam().isBlank()
                && event.getHomeTeam().toLowerCase().contains("wydad");
    }

    private void notifyPlayers(List<AuthClient.PlayerRecipient> players, Long eventId, int billetsCrees) {
        for (var player : players) {
            try {
                notificationClient.notifyUser(
                        player.id(),
                        player.email(),
                        "Billets VIP disponibles",
                        BILLETS_PAR_JOUEUR + " billets VIP vous sont offerts pour ce match à domicile.",
                        "/joueur/billets");
            } catch (Exception e) {
                log.warn("Notification VIP non envoyée à {}", player.email(), e);
            }
        }
    }
}
