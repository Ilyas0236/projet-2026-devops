package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.ConvocationResponse;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.PlayerDocumentResponse;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.RespondRequest;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.UpdateMyProfileRequest;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Convocation;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.PlayerDocument;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.repository.ConvocationRepository;
import com.wydad.digital.sports.repository.PlayerDocumentRepository;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.SessionRepository;
import com.wydad.digital.sports.filter.SportsUserContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Logique métier de l'espace joueur (B.3 / B.3.a) :
 * <ul>
 *   <li>convocations : création staff + notification automatique, réponse
 *       du joueur sur SES convocations uniquement (ownership serveur) ;</li>
 *   <li>documents : partage staff/admin, lecture par le destinataire ;</li>
 *   <li>profil : le joueur ne peut éditer que taille/poids/naissance/
 *       nationalité/photo — numéro, poste et catégorie restent admin.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PlayerSpaceService {

    private final ConvocationRepository convocationRepository;
    private final PlayerDocumentRepository playerDocumentRepository;
    private final PlayerRepository playerRepository;
    private final SessionRepository sessionRepository;
    private final NotificationClient notificationClient;

    // ─────────────────────────── CONVOCATIONS ───────────────────────────

    /**
     * B.3.a — Création d'une convocation par le STAFF (ou ADMIN). Génère
     * automatiquement une notification IN_APP au joueur concerné.
     */
    @Transactional
    public ConvocationResponse createConvocation(Long joueurUserId, Long sessionId, Long staffId) {
        Player player = playerRepository.findByUserId(joueurUserId)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé: " + joueurUserId));
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Séance non trouvée: " + sessionId));

        // Un joueur ne peut être convoqué qu'une fois par séance.
        if (convocationRepository.existsByJoueurUserIdAndSession_Id(joueurUserId, sessionId)) {
            throw new IllegalStateException("Ce joueur est déjà convoqué pour cette séance");
        }

        // B.6 — Statut médical strict : un joueur INAPTE ne peut pas être convoqué.
        if (player.getMedicalStatus() == com.wydad.digital.sports.enums.MedicalStatus.INAPTE) {
            throw new IllegalStateException(
                    "Convocation impossible : le joueur est INAPTE (statut médical)");
        }

        Convocation saved = convocationRepository.save(Convocation.builder()
                .joueurUserId(joueurUserId)
                .session(session)
                .sportType(session.getSportType())
                .category(session.getCategory())
                .createdByStaffId(staffId)
                .build());

        notifyConvocation(saved);
        return toResponse(saved);
    }

    /** Notifications best-effort à chaque convocation (B.3.a). */
    private void notifyConvocation(Convocation c) {
        var s = c.getSession();
        String when = s != null && s.getSessionDate() != null
                ? s.getSessionDate().toLocalDate().toString() : "";
        notificationClient.notifyUser(
                c.getJoueurUserId(),
                null,
                "Nouvelle convocation",
                "Vous êtes convoqué" + (when.isEmpty() ? "" : " le " + when)
                        + ". Consultez votre espace joueur.",
                "/joueur/dashboard");
    }

    /** Convocations du joueur connecté — toujours filtrées par son userId JWT. */
    public List<ConvocationResponse> getMyConvocations() {
        Long me = requireCurrentUserId();
        return convocationRepository
                .findByJoueurUserIdOrderBySession_SessionDateAsc(me)
                .stream().map(this::toResponse).toList();
    }

    /** Historique de présence : réponses déjà données par le joueur. */
    public List<ConvocationResponse> getMyAttendanceHistory() {
        Long me = requireCurrentUserId();
        return convocationRepository
                .findByJoueurUserIdAndResponseStatusIsNotNullOrderByRespondedAtDesc(me)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Réponse à une convocation : ownership strict — un joueur ne peut
     * répondre que sur SA convocation, sinon 403.
     */
    @Transactional
    public ConvocationResponse respondToConvocation(Long convocationId, RespondRequest request) {
        Long me = requireCurrentUserId();
        Convocation c = convocationRepository.findById(convocationId)
                .orElseThrow(() -> new EntityNotFoundException("Convocation non trouvée: " + convocationId));

        if (!c.getJoueurUserId().equals(me)) {
            throw new AccessDeniedException("Réponse à la convocation d'un autre joueur interdite");
        }
        if (request.status() == null) {
            throw new IllegalArgumentException("Le statut de réponse est obligatoire");
        }
        if ((request.status() == Convocation.ResponseStatus.ABSENT
                || request.status() == Convocation.ResponseStatus.RETARD)
                && (request.justification() == null || request.justification().isBlank())) {
            throw new IllegalArgumentException("Une justification est obligatoire pour ABSENT ou RETARD");
        }

        c.setResponseStatus(request.status());
        c.setResponseJustification(
                request.status() == Convocation.ResponseStatus.CONFIRME ? null : request.justification());
        c.setRespondedAt(java.time.LocalDateTime.now());
        return toResponse(convocationRepository.save(c));
    }

    // ───────────────────────────── DOCUMENTS ─────────────────────────────

    /** Partage d'un document avec un joueur (staff/admin). */
    public PlayerDocumentResponse shareDocument(Long joueurUserId, String title, String url) {
        if (title == null || title.isBlank() || url == null || url.isBlank()) {
            throw new IllegalArgumentException("Titre et URL sont obligatoires");
        }
        PlayerDocument doc = PlayerDocument.builder()
                .joueurUserId(joueurUserId)
                .title(title.trim())
                .url(url.trim())
                .build();
        return toResponse(playerDocumentRepository.save(doc));
    }

    /** Documents adressés au joueur connecté uniquement. */
    public List<PlayerDocumentResponse> getMyDocuments() {
        Long me = requireCurrentUserId();
        return playerDocumentRepository.findByJoueurUserIdOrderByDateAjoutDesc(me)
                .stream().map(this::toResponse).toList();
    }

    // ────────────────────────────── PROFIL ──────────────────────────────

    /**
     * Édition de profil par le joueur : seuls les champs autorisés sont
     * appliqués (jamais numéro/poste/catégorie/sport).
     */
    @Transactional
    public com.wydad.digital.sports.dto.PlayerDto updateMyProfile(UpdateMyProfileRequest req) {
        Long me = requireCurrentUserId();
        Player p = playerRepository.findByUserId(me)
                .orElseThrow(() -> new EntityNotFoundException("Aucune fiche sportive liée à votre compte"));

        if (req.height() != null) p.setHeight(req.height());
        if (req.weight() != null) p.setWeight(req.weight());
        if (req.birthDate() != null) p.setBirthDate(req.birthDate());
        if (req.nationality() != null) p.setNationality(req.nationality());
        if (req.photoUrl() != null) p.setPhotoUrl(req.photoUrl());

        // Recalcul de l'IMC si taille+poids connus (même règle que le service admin)
        if (p.getWeight() != null && p.getHeight() != null && p.getHeight() > 0) {
            double hM = p.getHeight() / 100.0;
            p.setBmi(Math.round(p.getWeight() / (hM * hM) * 100.0) / 100.0);
        }

        return toPlayerDto(playerRepository.save(p));
    }

    private com.wydad.digital.sports.dto.PlayerDto toPlayerDto(Player p) {
        var dto = new com.wydad.digital.sports.dto.PlayerDto();
        dto.setId(p.getId());
        dto.setUserId(p.getUserId());
        dto.setFullName(p.getFullName());
        dto.setSportType(p.getSportType());
        dto.setCategory(p.getCategory());
        dto.setPosition(p.getPosition());
        dto.setJerseyNumber(p.getJerseyNumber());
        dto.setHeight(p.getHeight());
        dto.setWeight(p.getWeight());
        dto.setBmi(p.getBmi());
        dto.setBirthDate(p.getBirthDate());
        dto.setNationality(p.getNationality());
        dto.setPhotoUrl(p.getPhotoUrl());
        dto.setMatchesPlayed(p.getMatchesPlayed());
        dto.setGoals(p.getGoals());
        dto.setAssists(p.getAssists());
        return dto;
    }

    // ──────────────────────────── HELPERS ────────────────────────────

    /** Accès lecture entité pour la vérification staff-catégorie du contrôleur. */
    public Player getPlayerEntity(Long joueurUserId) {
        return playerRepository.findByUserId(joueurUserId)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé: " + joueurUserId));
    }

    private Long requireCurrentUserId() {
        Long id = SportsUserContext.getCurrentUserId();
        if (id == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        return id;
    }

    private ConvocationResponse toResponse(Convocation c) {
        var s = c.getSession();
        return ConvocationResponse.builder()
                .id(c.getId())
                .sessionId(s != null ? s.getId() : null)
                .sessionTitle(s != null ? s.getTitle() : null)
                .sessionLocation(s != null ? s.getLocation() : null)
                .sessionDate(s != null ? s.getSessionDate() : null)
                .sportType(c.getSportType())
                .category(c.getCategory())
                .responseStatus(c.getResponseStatus())
                .responseJustification(c.getResponseJustification())
                .respondedAt(c.getRespondedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private PlayerDocumentResponse toResponse(PlayerDocument d) {
        return PlayerDocumentResponse.builder()
                .id(d.getId())
                .title(d.getTitle())
                .url(d.getUrl())
                .dateAjout(d.getDateAjout())
                .build();
    }
}
