package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.ContentClient;
import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.dto.MatchConvocationDtos.BatchMatchConvocationRequest.BatchPlayerEntry;
import com.wydad.digital.sports.dto.MatchConvocationDtos.PublicConvocationView.PublishedPlayer;
import com.wydad.digital.sports.dto.MatchConvocationDtos.*;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.MatchConvocation;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.MatchConvocationRepository;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Convocations de match (§8) : l'entraîneur du groupe prépare la feuille
 * (titulaires / remplaçants), la soumet à l'ADMIN qui la publie (§9).
 *
 * <p>Règles de sécurité (§24/§26) :</p>
 * <ul>
 *   <li>le match doit EXISTER côté content-service — sa discipline et sa
 *       catégorie font foi, pas celles annoncées par le client ;</li>
 *   <li>seul le staff encadrant la discipline+catégorie du match peut
 *       convoquer ;</li>
 *   <li>les joueurs convoqués doivent appartenir au groupe du match ;</li>
 *   <li>la publication est réservée à l'ADMIN.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchConvocationService {

    private final MatchConvocationRepository repository;
    private final PlayerRepository playerRepository;
    private final StaffRepository staffRepository;
    private final ContentClient contentClient;
    private final NotificationClient notificationClient;

    // ────────────────────── ENTRAÎNEUR (§8) ──────────────────────

    /** Convocation groupée d'un match : N joueurs avec leur rôle. */
    @Transactional
    public BatchResult convocateBatch(BatchMatchConvocationRequest request) {
        if (request.matchId() == null || request.players() == null || request.players().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Match et liste des joueurs requis");
        }

        // 1. Le match doit exister — sa fiche fait foi (discipline + catégorie).
        ResolvedMatch match = requireMatch(request.matchId());

        // 2. L'appelant doit encadrer ce groupe.
        Long staffUserId = requireCoachOfGroup(match);

        int created = 0;
        List<MatchConvocationResponse> convocations = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (BatchPlayerEntry entry : request.players()) {
            try {
                Player player = playerRepository.findByUserId(entry.joueurUserId())
                        .orElseThrow(() -> new IllegalArgumentException("Joueur introuvable"));
                if (player.getSportType() != match.sportType()
                        || player.getCategory() != match.category()) {
                    throw new IllegalArgumentException(
                            "Le joueur n'appartient pas au groupe du match");
                }
                MatchConvocation c = repository.save(MatchConvocation.builder()
                        .matchId(match.id())
                        .sportType(match.sportType())
                        .category(match.category())
                        .joueurUserId(entry.joueurUserId())
                        .playerRole(roleOf(entry.playerRole()))
                        .status(MatchConvocation.PublicationStatus.DRAFT)
                        .createdByStaffUserId(staffUserId)
                        .build());
                created++;
                convocations.add(toResponse(c));

                // Notification in-app best-effort (§26 : notifications groupées).
                notificationClient.notifyUser(player.getUserId(), null,
                        "Convocation", "Vous êtes convoqué pour le match vs "
                                + match.adversaire(),
                        "/joueur/dashboard");
            } catch (IllegalArgumentException e) {
                rejected.add("Joueur " + entry.joueurUserId() + " : " + e.getMessage());
            }
        }
        return new BatchResult(created, convocations, rejected);
    }

    /** Liste des joueurs qu'un entraîneur PEUT sélectionner pour un match. */
    public List<Player> selectablePlayers(Long matchId) {
        ResolvedMatch match = requireMatch(matchId);
        requireCoachOfGroup(match);
        return playerRepository.findBySportTypeAndCategory(match.sportType(), match.category());
    }

    /** Feuille de match d'un entraîneur/staff du groupe. */
    public List<MatchConvocationResponse> sheetForStaff(Long matchId) {
        ResolvedMatch match = requireMatch(matchId);
        requireCoachOfGroup(match);
        return toResponses(repository.findByMatchIdOrderByPlayerRoleAscIdAsc(matchId));
    }

    /** Feuille d'un joueur connecté : ses propres convocations de match. */
    public List<MatchConvocationResponse> mySheet(Long joueurUserId) {
        return toResponses(repository.findByJoueurUserIdOrderByMatchIdDesc(joueurUserId));
    }

    /**
     * Soumission à l'ADMIN (§9) : passe toute la feuille DRAFT → SOUMISE.
     * Seul un staff du groupe peut soumettre.
     */
    @Transactional
    public List<MatchConvocationResponse> submitToAdmin(Long matchId) {
        ResolvedMatch match = requireMatch(matchId);
        requireCoachOfGroup(match);
        List<MatchConvocation> sheet = repository.findByMatchIdOrderByPlayerRoleAscIdAsc(matchId);
        if (sheet.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun joueur convoqué pour ce match");
        }
        for (MatchConvocation c : sheet) {
            if (c.getStatus() == MatchConvocation.PublicationStatus.DRAFT
                    || c.getStatus() == MatchConvocation.PublicationStatus.REFUSEE) {
                c.setStatus(MatchConvocation.PublicationStatus.SOUMISE);
                c.setSubmittedAt(java.time.LocalDateTime.now());
            }
        }
        return toResponses(repository.saveAll(sheet));
    }

    // ────────────────────── ADMIN (§9) ──────────────────────

    /** Feuilles soumises, vue ADMIN (toutes disciplines confondues). */
    public List<MatchConvocationResponse> submittedSheets() {
        return toResponses(repository.findByStatus(MatchConvocation.PublicationStatus.SOUMISE));
    }

    /** Publication ADMIN : toute la feuille du match passe PUBLIEE. */
    @Transactional
    public List<MatchConvocationResponse> publish(Long matchId) {
        List<MatchConvocation> sheet = repository.findByMatchIdOrderByPlayerRoleAscIdAsc(matchId);
        if (sheet.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Aucune convocation pour ce match");
        }
        for (MatchConvocation c : sheet) {
            if (c.getStatus() != MatchConvocation.PublicationStatus.PUBLIEE) {
                c.setStatus(MatchConvocation.PublicationStatus.PUBLIEE);
                c.setPublishedAt(java.time.LocalDateTime.now());
            }
        }
        // §9 : la liste publiée apparaît automatiquement sur le site public.
        return toResponses(repository.saveAll(sheet));
    }

    /** Refus ADMIN (avec motif). */
    @Transactional
    public List<MatchConvocationResponse> reject(Long matchId, String reason) {
        List<MatchConvocation> sheet = repository.findByMatchIdOrderByPlayerRoleAscIdAsc(matchId);
        for (MatchConvocation c : sheet) {
            if (c.getStatus() == MatchConvocation.PublicationStatus.SOUMISE) {
                c.setStatus(MatchConvocation.PublicationStatus.REFUSEE);
                c.setRejectionReason(reason == null ? "Non conforme" : reason);
            }
        }
        return toResponses(repository.saveAll(sheet));
    }

    /** Vue publique (site vitrine) : uniquement les feuilles PUBLIEES. */
    public PublicConvocationView publicView(Long matchId) {
        List<MatchConvocation> published =
                repository.findByMatchIdOrderByPlayerRoleAscIdAsc(matchId).stream()
                        .filter(c -> c.getStatus() == MatchConvocation.PublicationStatus.PUBLIEE)
                        .toList();
        if (published.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Liste non publiée pour ce match");
        }
        List<PublishedPlayer> titulaires = new ArrayList<>();
        List<PublishedPlayer> remplacants = new ArrayList<>();
        for (MatchConvocation c : published) {
            String name = playerRepository.findByUserId(c.getJoueurUserId())
                    .map(Player::getFullName).orElse("Joueur #" + c.getJoueurUserId());
            Integer jersey = playerRepository.findByUserId(c.getJoueurUserId())
                    .map(Player::getJerseyNumber).orElse(null);
            var p = PublicConvocationView.PublishedPlayer.builder()
                    .fullName(name).jerseyNumber(jersey).build();
            if (c.getPlayerRole() == MatchConvocation.PlayerRole.TITULAIRE) {
                titulaires.add(p);
            } else {
                remplacants.add(p);
            }
        }
        return PublicConvocationView.builder()
                .matchId(matchId)
                .sportType(published.get(0).getSportType())
                .category(published.get(0).getCategory())
                .titulaires(titulaires)
                .remplacants(remplacants)
                .publishedAt(published.get(0).getPublishedAt())
                .build();
    }

    // ────────────────────── HELPERS ──────────────────────

    /** Fiche match normalisée : discipline + catégorie validées côté serveur. */
    private record ResolvedMatch(Long id, String adversaire, String lieu,
                                 SportType sportType, Category category) {}

    /** Charge la fiche match depuis content-service (source de vérité §16). */
    private ResolvedMatch requireMatch(Long matchId) {
        ContentClient.MatchInfo match = contentClient.fetchMatch(matchId);
        if (match == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Match inexistant — convocation impossible (§17/§8)");
        }
        SportType sportType;
        Category category;
        try {
            sportType = SportType.valueOf(match.sport());
            category = Category.valueOf(match.categorie());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le match n'a pas de discipline/catégorie valide");
        }
        return new ResolvedMatch(match.id(), match.adversaire(), match.lieu(),
                sportType, category);
    }

    /**
     * Vérifie que l'appelant est ENTRAINEUR/STAFF du groupe du match (ou
     * ADMIN) et renvoie son userId.
     */
    private Long requireCoachOfGroup(ResolvedMatch match) {
        String role = SportsUserContext.getCurrentUserRole();
        if ("ADMIN".equals(role)) {
            return 0L;
        }
        if (!"ENTRAINEUR".equals(role) && !"STAFF".equals(role)) {
            throw new AccessDeniedException(
                    "Seul l'encadrement du groupe peut gérer les convocations");
        }
        Staff staff = staffRepository.findByUserId(SportsUserContext.getCurrentUserId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Aucun profil encadrement rattaché à ce compte"));
        if (staff.getSportType() != match.sportType()
                || staff.getAssignedCategory() != match.category()) {
            throw new AccessDeniedException(
                    "Cette feuille de match concerne une autre équipe");
        }
        return staff.getUserId();
    }

    private MatchConvocation.PlayerRole roleOf(MatchConvocation.PlayerRole role) {
        return role == null ? MatchConvocation.PlayerRole.REMPLACANT : role;
    }

    private List<MatchConvocationResponse> toResponses(List<MatchConvocation> list) {
        return list.stream().map(this::toResponse).toList();
    }

    private MatchConvocationResponse toResponse(MatchConvocation c) {
        String name = playerRepository.findByUserId(c.getJoueurUserId())
                .map(Player::getFullName).orElse("Joueur #" + c.getJoueurUserId());
        Integer jersey = playerRepository.findByUserId(c.getJoueurUserId())
                .map(Player::getJerseyNumber).orElse(null);
        // Adversaire enrichi depuis la fiche match content-service (best-effort :
        // si la fiche est indisponible, l'affichage retombe sur « Match #id »).
        String adversaire = null;
        ContentClient.MatchInfo match = contentClient.fetchMatch(c.getMatchId());
        if (match != null) { adversaire = match.adversaire(); }
        return MatchConvocationResponse.builder()
                .id(c.getId())
                .matchId(c.getMatchId())
                .sportType(c.getSportType())
                .category(c.getCategory())
                .adversaire(adversaire)
                .jerseyNumber(jersey)
                .joueurUserId(c.getJoueurUserId())
                .joueurName(name)
                .playerRole(c.getPlayerRole())
                .status(c.getStatus())
                .submittedAt(c.getSubmittedAt())
                .publishedAt(c.getPublishedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
