package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.BatchConvocationRequest;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.BatchConvocationResponse;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.BatchRejection;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.ConvocationResponse;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.PlayerDocumentResponse;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.RespondRequest;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.SessionResponsesSummary;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.StaffConvocationView;
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
import com.wydad.digital.sports.exception.MediaIndisponibleException;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Staff;
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
    private final MediaStorageService mediaStorageService;
    private final com.wydad.digital.sports.repository.StaffRepository staffRepository;

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

    /**
     * Phase 3 — accusé de lecture : appelé quand le joueur consulte SA
     * convocation. Ownership strict (sinon 403), idempotent (re-lecture
     * ne change pas la date).
     */
    @Transactional
    public ConvocationResponse markConvocationRead(Long convocationId) {
        Long me = requireCurrentUserId();
        Convocation c = convocationRepository.findById(convocationId)
                .orElseThrow(() -> new EntityNotFoundException("Convocation non trouvée: " + convocationId));
        if (!c.getJoueurUserId().equals(me)) {
            throw new AccessDeniedException("Lecture de la convocation d'un autre joueur interdite");
        }
        if (c.getReadAt() == null) {
            c.setReadAt(java.time.LocalDateTime.now());
            c = convocationRepository.save(c);
        }
        return toResponse(c);
    }

    // ────────────────── Phase 3 — STAFF : groupage & suivi ──────────────────

    /**
     * Phase 3 — convocation GROUPÉE (« liste cochable » du formulaire
     * entraîneur) : une séance, N joueurs, un seul appel HTTP. Chaque joueur
     * est traité individuellement : un doublon ou un joueur INAPTE ne bloque
     * pas les autres — il alimente la liste des rejets motivés.
     * Une notification in-app part pour chaque convocation réellement créée.
     */
    @Transactional
    public BatchConvocationResponse createBatchConvocation(BatchConvocationRequest request, Long staffId) {
        if (request.joueurUserIds() == null || request.joueurUserIds().isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins un joueur");
        }
        if (request.sessionId() == null) {
            throw new IllegalArgumentException("La séance est obligatoire");
        }

        List<ConvocationResponse> created = new java.util.ArrayList<>();
        List<BatchRejection> rejected = new java.util.ArrayList<>();

        // Pas de déduplication silencieuse : un doublon de la liste est
        // signalé comme rejet motivé (anti-doublon par séance).
        for (Long joueurUserId : request.joueurUserIds()) {
            try {
                created.add(createConvocation(joueurUserId, request.sessionId(), staffId));
            } catch (EntityNotFoundException | IllegalStateException e) {
                rejected.add(BatchRejection.builder()
                        .joueurUserId(joueurUserId)
                        .reason(e.getMessage())
                        .build());
            }
        }
        return BatchConvocationResponse.builder()
                .created(created.size())
                .convocations(created)
                .rejected(rejected)
                .build();
    }

    /**
     * Phase 3 — vue entraîneur : toutes les convocations d'une séance avec
     * réponse présence ET accusé de lecture de chaque joueur.
     */
    public List<StaffConvocationView> getSessionResponses(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Séance non trouvée: " + sessionId));
        return convocationRepository.findBySession_IdOrderByCreatedAtAsc(session.getId())
                .stream().map(this::toStaffView).toList();
    }

    /** Compteurs de suivi d'une séance (lu/non lu, confirmés/absents/retards). */
    public SessionResponsesSummary getSessionSummary(Long sessionId) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Séance non trouvée: " + sessionId));
        List<Convocation> all = convocationRepository.findBySession_IdOrderByCreatedAtAsc(sessionId);
        long unread = all.stream().filter(c -> c.getReadAt() == null).count();
        long confirmed = all.stream()
                .filter(c -> c.getResponseStatus() == Convocation.ResponseStatus.CONFIRME).count();
        long absent = all.stream()
                .filter(c -> c.getResponseStatus() == Convocation.ResponseStatus.ABSENT).count();
        long late = all.stream()
                .filter(c -> c.getResponseStatus() == Convocation.ResponseStatus.RETARD).count();
        long pending = all.stream().filter(c -> c.getResponseStatus() == null).count();
        return SessionResponsesSummary.builder()
                .sessionId(sessionId)
                .total(all.size())
                .unread(unread)
                .confirmed(confirmed)
                .absent(absent)
                .late(late)
                .pending(pending)
                .build();
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

    // ───────────────────────── DOCUMENTS / MÉDIAS ─────────────────────────

    /** Partage d'un document (référence URL existante) avec un joueur. */
    public PlayerDocumentResponse shareDocument(Long joueurUserId, String title, String url) {
        if (title == null || title.isBlank() || url == null || url.isBlank()) {
            throw new IllegalArgumentException("Titre et URL sont obligatoires");
        }
        PlayerDocument doc = PlayerDocument.builder()
                .joueurUserId(joueurUserId)
                .recipientUserIds(java.util.Set.of(joueurUserId))
                .title(title.trim())
                .url(url.trim())
                .mediaType(PlayerDocument.MediaType.DOCUMENT)
                .build();
        return toResponse(playerDocumentRepository.save(doc));
    }

    /**
     * Phase 3 — partage d'un média tactique AVEC UPLOAD RÉEL : le staff
     * envoie un fichier (vidéo/photo/PDF) stocké sur Cloudinary, adressé à
     * UN joueur ou à TOUTE la catégorie qu'il encadre.
     */
    @Transactional
    public PlayerDocumentResponse shareMedia(
            Long senderUserId,
            String title,
            String message,
            PlayerDocument.MediaType mediaType,
            org.springframework.web.multipart.MultipartFile file,
            Long joueurUserId,
            boolean wholeTeam) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }

        // Destinataires : un joueur précis ou toute l'équipe de la catégorie du staff.
        java.util.Set<Long> recipients;
        Long primaryRecipient;
        Category teamCategory = null;
        SportType teamSport = null;
        if (wholeTeam) {
            Staff staff = staffRepository.findByUserId(senderUserId)
                    .orElseThrow(() -> new AccessDeniedException("Aucun profil staff lié à votre compte"));
            List<Player> team = playerRepository.findBySportTypeAndCategory(
                    staff.getSportType(), staff.getAssignedCategory());
            if (team.isEmpty()) {
                throw new IllegalStateException("Aucun joueur dans votre catégorie");
            }
            teamSport = staff.getSportType();
            teamCategory = staff.getAssignedCategory();
            recipients = team.stream().map(Player::getUserId)
                    .collect(java.util.stream.Collectors.toSet());
            primaryRecipient = recipients.iterator().next(); // compat colonne NOT NULL
        } else {
            if (joueurUserId == null) {
                throw new IllegalArgumentException("Choisissez un joueur ou cochez « toute l'équipe »");
            }
            recipients = java.util.Set.of(joueurUserId);
            primaryRecipient = joueurUserId;
        }

        // Upload réel du fichier (Cloudinary ; référence locale en mode dégradé).
        MediaStorageService.UploadResult upload;
        try {
            upload = mediaStorageService.uploadMedia(file, "staff-" + senderUserId);
        } catch (java.io.IOException e) {
            throw new MediaIndisponibleException(e.getMessage());
        }

        PlayerDocument doc = PlayerDocument.builder()
                .senderUserId(senderUserId)
                .joueurUserId(primaryRecipient)
                .recipientUserIds(recipients)
                .title(title.trim())
                .message(message)
                .mediaType(resolveMediaType(mediaType, file))
                .publicId(upload.publicId())
                .url(upload.secureUrl() != null ? upload.secureUrl() : upload.publicId())
                .sportType(teamSport)
                .category(teamCategory)
                .wholeTeam(wholeTeam)
                .build();
        PlayerDocument saved = playerDocumentRepository.save(doc);

        // Notification in-app best-effort à chaque destinataire.
        for (Long uid : recipients) {
            try {
                notificationClient.notifyUser(uid, null,
                        "Nouveau média de votre entraîneur",
                        title.trim() + (message != null && !message.isBlank()
                                ? " — " + message : "") + ". Consultez votre espace joueur.",
                        "/joueur/dashboard");
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(PlayerSpaceService.class).warn(
                        "Notification média non envoyée a user {}: {}", uid, e.getMessage());
            }
        }
        return toResponse(saved);
    }

    /** Médias émis par un staff (suivi côté entraîneur). */
    public List<PlayerDocumentResponse> getSentMedia(Long senderUserId) {
        return playerDocumentRepository.findBySenderUserIdOrderByDateAjoutDesc(senderUserId)
                .stream().map(this::toResponse).toList();
    }

    /** Type de média : fourni par le staff, sinon déduit du type MIME du fichier. */
    private PlayerDocument.MediaType resolveMediaType(
            PlayerDocument.MediaType requested,
            org.springframework.web.multipart.MultipartFile file) {
        if (requested != null) {
            return requested;
        }
        String mime = file != null ? file.getContentType() : null;
        if (mime == null) {
            return PlayerDocument.MediaType.DOCUMENT;
        }
        if (mime.startsWith("video/")) {
            return PlayerDocument.MediaType.VIDEO;
        }
        if (mime.startsWith("image/")) {
            return PlayerDocument.MediaType.PHOTO;
        }
        return PlayerDocument.MediaType.DOCUMENT;
    }

    /** Documents adressés au joueur connecté uniquement. */
    public List<PlayerDocumentResponse> getMyDocuments() {
        Long me = requireCurrentUserId();
        return playerDocumentRepository.findAllAddressedTo(me)
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
                .readAt(c.getReadAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    /** Phase 3 — vue entraîneur enrichie du nom du joueur. */
    private StaffConvocationView toStaffView(Convocation c) {
        String name = playerRepository.findByUserId(c.getJoueurUserId())
                .map(Player::getFullName).orElse("Joueur #" + c.getJoueurUserId());
        return StaffConvocationView.builder()
                .id(c.getId())
                .joueurUserId(c.getJoueurUserId())
                .joueurName(name)
                .responseStatus(c.getResponseStatus())
                .responseJustification(c.getResponseJustification())
                .respondedAt(c.getRespondedAt())
                .readAt(c.getReadAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private PlayerDocumentResponse toResponse(PlayerDocument d) {
        // URL signée à la demande (1 h) pour les médias Cloudinary.
        String url = mediaStorageService.signedUrl(d.getPublicId(), d.getUrl());
        // Nom de l'expéditeur (affichage « de la part de … » côté front).
        String senderName = d.getSenderUserId() == null ? null
                : staffRepository.findByUserId(d.getSenderUserId())
                        .map(com.wydad.digital.sports.model.Staff::getFullName)
                        .orElse(null);
        return PlayerDocumentResponse.builder()
                .id(d.getId())
                .title(d.getTitle())
                .url(url != null ? url : d.getUrl())
                .dateAjout(d.getDateAjout())
                .mediaType(d.getMediaType())
                .message(d.getMessage())
                .senderUserId(d.getSenderUserId())
                .senderName(senderName)
                .publicId(d.getPublicId())
                .build();
    }
}
