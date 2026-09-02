package com.wydad.digital.ticket.service;

import com.wydad.digital.ticket.client.AuthClient;
import com.wydad.digital.ticket.client.NotificationClient;
import com.wydad.digital.ticket.client.SportsRosterClient;
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
 * Phase 2 / §3-§4 — Génération automatique des billets VIP.
 *
 * À chaque match du Wydad À DOMICILE, chaque MEMBRE DU GROUPE concerné
 * (discipline + catégorie, §26) reçoit des billets VIP gratuits (invités du
 * club) : 4 billets pour les SENIOR, 2 billets pour les catégories jeunes
 * (U15/U17/U18/U20). Les billets sont attachés à son compte et
 * téléchargeables en PDF avec QR unique depuis son espace.
 *
 * <p>B.29 — « MEMBRE » = JOUEUR + STAFF (entraineurs, manager, fitness,
 * etc.) du groupe. Avant B.29 seuls les JOUEUR étaient servis (filtre
 * {@code "JOUEUR".equals(...)} dans {@code SportsRosterClient}) — staff
 * et entraineurs étaient droppés silencieusement.</p>
 *
 * Règles métier :
 * - Match à l'extérieur → aucun billet (rejet explicite).
 * - §24/§26 : seuls les membres de la discipline+catégorie de l'événement
 *   sont servis — jamais un joueur Football U17 sur un match Basketball U17.
 *   Événement sans catégorie (historique) → tous les joueurs actifs
 *   (fallback auth, STAFF non couvert par ce fallback).
 * - Idempotent : relancer la génération pour un événement déjà traité ne
 *   crée AUCUN doublon — seuls les membres manquants sont servis.
 * - Hors circuit de vente : prix 0, statut PAID directement (offre du club),
 *   PAS de débit E-cash — ces billets ne passent jamais par PaymentClient.
 * - Les places VIP ne touchent pas au compteur public availableSeats :
 *   une section VIP dédiée porte la capacité d'accueil des invités.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipTicketService {

    /** Billets VIP offerts par membre SENIOR à domicile (§3). */
    static final int BILLETS_PAR_JOUEUR_SENIOR = 4;

    /** Billets VIP adaptés par membre d'une catégorie jeune (§4). */
    static final int BILLETS_PAR_JOUEUR_JEUNE = 2;

    private final EventRepository eventRepository;
    private final SectionRepository sectionRepository;
    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final AuthClient authClient;
    private final SportsRosterClient rosterClient;
    private final NotificationClient notificationClient;

    /**
     * Résultat d'une génération : compteurs pour journalisation et tests.
     * <p>B.29 — {@code joueursServis} est renommé sémantiquement en
     * « beneficiairesServis » côté réponse HTTP mais conserve ce nom en
     * interne pour rétro-compat avec l'endpoint interne /vip-generate.</p>
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
            // Section VIP absente, sports/auth injoignable... : l'ADMIN peut
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

        List<Recipient> recipients = recipientsForEvent(event);
        int billets = 0;
        int beneficiairesServis = 0;

        for (var recipient : recipients) {
            if (ticketRepository.existsByEventIdAndUserIdAndCategory(eventId, recipient.id(), TicketCategory.VIP)) {
                continue; // déjà servi (relance/idempotence)
            }
            for (int i = 0; i < recipient.billets(); i++) {
                String ticketNumber = "WAC-VIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String qrData = "WAC-TICKET:" + ticketNumber + ":EVENT:" + event.getId() + ":USER:" + recipient.id();
                ticketRepository.save(Ticket.builder()
                        .ticketNumber(ticketNumber)
                        .userId(recipient.id())
                        .userFullName(recipient.displayName())
                        .userEmail(recipient.email())
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
            sectionVip.setAvailableSeats(Math.max(sectionVip.getAvailableSeats() - recipient.billets(), 0));
            billets += recipient.billets();
            beneficiairesServis++;
        }

        sectionRepository.save(sectionVip);

        // Best-effort : prévenir chaque membre nouvellement servi.
        notifyPlayers(recipients, eventId, billets);

        log.info("Génération VIP événement {} : {} bénéficiaire(s) servi(s) ({} billet(s))",
                eventId, beneficiairesServis, billets);
        return new VipGenerationResult(beneficiairesServis, billets);
    }

    /** Un match est « à domicile » si le Wydad est l'équipe recevante. */
    private boolean isHomeMatch(Event event) {
        return event.getHomeTeam() != null && !event.getHomeTeam().isBlank()
                && event.getAwayTeam() != null && !event.getAwayTeam().isBlank()
                && event.getHomeTeam().toLowerCase().contains("wydad");
    }

    /**
     * §24/§26 — destinataires des billets : les MEMBRES du groupe
     * discipline+catégorie de l'événement (roster serveur), 4 billets en
     * SENIOR et 2 en catégorie jeune. Sans catégorie renseignée
     * (événements historiques), repli sur tous les joueurs actifs avec le
     * quota SENIOR (le staff n'a pas d'annuaire de repli — ces events
     * historiques ne distribuent donc que les joueurs).
     *
     * <p>B.29 — utilise {@code fetchMembersOfGroup} (sans filtre JOUEUR) :
     * JOUEUR + STAFF sont servis identiquement. Le rôle est mémorisé dans
     * le {@code Recipient} pour adapter le texte de notification.</p>
     */
    private List<Recipient> recipientsForEvent(Event event) {
        int billetsParMembre = event.getCategory() == null
                ? BILLETS_PAR_JOUEUR_SENIOR
                : billetsPourCategorie(event.getCategory().name());

        if (event.getCategory() == null) {
            return authClient.fetchActivePlayers().stream()
                    .map(p -> new Recipient(p.id(), p.email(), p.displayName(),
                            billetsParMembre, "JOUEUR"))
                    .toList();
        }

        return rosterClient.fetchMembersOfGroup(event.getEventType().name(), event.getCategory().name())
                .stream()
                .map(m -> new Recipient(
                        m.userId(),
                        null, // email : pas dans roster, on l'envoie sans (notification accepte null)
                        m.fullName(),
                        billetsParMembre,
                        m.rosterRole() == null ? "JOUEUR" : m.rosterRole()))
                .toList();
    }

    /** §3/§4 — quota de billets selon la catégorie du groupe. */
    static int billetsPourCategorie(String category) {
        return "SENIOR".equalsIgnoreCase(category) ? BILLETS_PAR_JOUEUR_SENIOR : BILLETS_PAR_JOUEUR_JEUNE;
    }

    /**
     * Destinataire normalisé. {@code role} = "JOUEUR" ou "STAFF" (couvre
     * entraineurs, manager, fitness, etc. car le roster endpoint n'expose
     * pas le StaffRole détaillé).
     */
    private record Recipient(Long id, String email, String displayName, int billets, String role) {}

    private void notifyPlayers(List<Recipient> recipients, Long eventId, int billetsCrees) {
        for (var recipient : recipients) {
            try {
                String prefix = "STAFF".equalsIgnoreCase(recipient.role())
                        ? "Vos (Staff technique)"
                        : "Vos";
                notificationClient.notifyUser(
                        recipient.id(),
                        recipient.email(),
                        "Billets VIP disponibles",
                        prefix + " " + recipient.billets()
                                + " billet(s) VIP vous sont offerts pour ce match à domicile.",
                        "/joueur/billets");
            } catch (Exception e) {
                log.warn("Notification VIP non envoyée à {}", recipient.displayName(), e);
            }
        }
    }
}
