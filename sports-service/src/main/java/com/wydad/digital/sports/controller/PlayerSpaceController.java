package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.dto.MatchStatDtos.MatchStatRequest;
import com.wydad.digital.sports.dto.MatchStatDtos.MatchStatResponse;
import com.wydad.digital.sports.dto.PlayerDto;
import com.wydad.digital.sports.dto.PlayerSpaceDtos;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.ConvocationResponse;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.PlayerDocumentResponse;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.RespondRequest;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.UpdateMyProfileRequest;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.repository.SessionRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import com.wydad.digital.sports.service.MedicalService;
import com.wydad.digital.sports.service.MatchStatService;
import com.wydad.digital.sports.service.PlayerSpaceService;
import com.wydad.digital.sports.dto.PlayerSpaceDtos.MedicalResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Espace joueur connecté (B.3 / B.3.a). Toutes les routes "my/*" dérivent
 * l'identité du contexte JWT — jamais du path — pour un ownership strict.
 */
@RestController
@RequestMapping("/api/sports/my-space")
@RequiredArgsConstructor
public class PlayerSpaceController {

    private final PlayerSpaceService playerSpaceService;
    private final MatchStatService matchStatService;
    private final MedicalService medicalService;
    private final StaffRepository staffRepository;
    private final SessionRepository sessionRepository;

    // ────────────────────── JOUEUR (routes self) ──────────────────────

    @GetMapping("/convocations")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<List<ConvocationResponse>> getMyConvocations() {
        return ResponseEntity.ok(playerSpaceService.getMyConvocations());
    }

    @GetMapping("/presence")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<List<ConvocationResponse>> getMyAttendanceHistory() {
        return ResponseEntity.ok(playerSpaceService.getMyAttendanceHistory());
    }

    @PostMapping("/convocations/{id}/respond")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<ConvocationResponse> respond(
            @PathVariable Long id,
            @RequestBody RespondRequest request) {
        return ResponseEntity.ok(playerSpaceService.respondToConvocation(id, request));
    }

    /**
     * Phase 3 — accusé de lecture : posé quand le joueur ouvre SA
     * convocation. Idempotent, ownership strict côté service.
     */
    @PostMapping("/convocations/{id}/read")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<ConvocationResponse> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(playerSpaceService.markConvocationRead(id));
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<List<PlayerDocumentResponse>> getMyDocuments() {
        return ResponseEntity.ok(playerSpaceService.getMyDocuments());
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<PlayerDto> updateMyProfile(@RequestBody UpdateMyProfileRequest request) {
        return ResponseEntity.ok(playerSpaceService.updateMyProfile(request));
    }

    /**
     * Photo de profil du joueur connecté (multipart, JPEG/PNG/WebP, max 5 Mo).
     * La fiche est résolue depuis le token — un joueur ne modifie jamais
     * la photo d'un autre.
     */
    @PostMapping(value = "/profile/photo", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<PlayerDto> uploadMyPhoto(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(playerSpaceService.uploadMyPhoto(file));
    }

    /** Statistiques de match détaillées du joueur connecté (B.4). */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('JOUEUR')")
    public ResponseEntity<List<MatchStatResponse>> getMyStats() {
        return ResponseEntity.ok(matchStatService.getMyStats());
    }

    // ─────────────────── STAFF / ADMIN (gestion) ───────────────────

    /**
     * B.3.a — Convocation d'un joueur. Seul le STAFF encadrant la catégorie
     * du joueur (ou l'ADMIN) peut convoquer — règle d'ownership vérifiée ici.
     */
    @PostMapping("/staff/convocations")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<ConvocationResponse> createConvocation(
            @RequestParam Long joueurUserId,
            @RequestParam Long sessionId) {
        Long staffId = ensureStaffCanManage(joueurUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playerSpaceService.createConvocation(joueurUserId, sessionId, staffId));
    }

    /**
     * Phase 3 — convocation GROUPÉE (« liste cochable ») : N joueurs pour
     * une séance en un appel. L'ownership catégorie est vérifié pour CHAQUE
     * joueur visé avant création ; les rejets sont motivés joueur par joueur.
     */
    @PostMapping("/staff/convocations/batch")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<PlayerSpaceDtos.BatchConvocationResponse> createBatchConvocation(
            @RequestBody PlayerSpaceDtos.BatchConvocationRequest request) {
        // Ownership catégorie vérifié joueur par joueur : le staffId passé
        // au service reste 0 si admin ; sinon on résout le profil staff une
        // fois puis le service rejoue les règles individuelles.
        Long staffId = resolveStaffIdentity();
        request.joueurUserIds().forEach(this::ensureStaffCanManage);
        return ResponseEntity.status(HttpStatus.OK)
                .body(playerSpaceService.createBatchConvocation(request, staffId));
    }

    /** Phase 3 — suivi des réponses d'une séance (vue entraîneur, ownership catégorie). */
    @GetMapping("/staff/sessions/{sessionId}/responses")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<List<PlayerSpaceDtos.StaffConvocationView>> getSessionResponses(
            @PathVariable Long sessionId) {
        ensureStaffOwnsSession(sessionId);
        return ResponseEntity.ok(playerSpaceService.getSessionResponses(sessionId));
    }

    /** Phase 3 — compteurs de suivi d'une séance (lu/non lu, présences, ownership catégorie). */
    @GetMapping("/staff/sessions/{sessionId}/responses/summary")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<PlayerSpaceDtos.SessionResponsesSummary> getSessionSummary(
            @PathVariable Long sessionId) {
        ensureStaffOwnsSession(sessionId);
        return ResponseEntity.ok(playerSpaceService.getSessionSummary(sessionId));
    }

    /** Partage d'un document médiathèque avec un joueur (staff/admin). */
    @PostMapping("/staff/documents")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<PlayerDocumentResponse> shareDocument(
            @RequestParam Long joueurUserId,
            @RequestBody ShareDocumentRequest body) {
        ensureStaffCanManage(joueurUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playerSpaceService.shareDocument(joueurUserId, body.title(), body.url()));
    }

    /**
     * Phase 3 — envoi d'un média tactique avec UPLOAD RÉEL (multipart) :
     * vidéo/photo/PDF stocké sur Cloudinary, adressé à UN joueur
     * ({@code joueurUserId}) ou à TOUTE l'équipe ({@code wholeTeam=true}).
     * Ownership catégorie vérifié pour chaque destinataire individuel ;
     * l'envoi équipe est borné à la catégorie du staff.
     */
    @PostMapping(value = "/staff/media", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<PlayerDocumentResponse> shareMedia(
            @RequestParam String title,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String mediaType,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false) Long joueurUserId,
            @RequestParam(defaultValue = "false") boolean wholeTeam) {
        Long senderUserId = SportsUserContext.getCurrentUserId();
        if (!wholeTeam && joueurUserId != null) {
            ensureStaffCanManage(joueurUserId);
        }
        com.wydad.digital.sports.model.PlayerDocument.MediaType type = null;
        if (mediaType != null && !mediaType.isBlank()) {
            try {
                type = com.wydad.digital.sports.model.PlayerDocument.MediaType
                        .valueOf(mediaType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Type inconnu -> déduit du fichier côté service.
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playerSpaceService.shareMedia(senderUserId, title, message, type, file,
                        joueurUserId, wholeTeam));
    }

    /** Phase 3 — médias émis par le staff connecté (suivi des envois). */
    @GetMapping("/staff/media/sent")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<List<PlayerDocumentResponse>> getSentMedia() {
        return ResponseEntity.ok(playerSpaceService.getSentMedia(
                SportsUserContext.getCurrentUserId()));
    }

    /**
     * B.4 — Saisie d'une statistique de match. Même scoping que les
     * convocations : STAFF de la catégorie du joueur uniquement,
     * ADMIN passe partout. Les totaux de la fiche sont recalculés.
     */
    @PostMapping("/staff/stats")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<MatchStatResponse> addPlayerStat(
            @RequestParam Long joueurUserId,
            @RequestBody MatchStatRequest body) {
        Long staffId = ensureStaffCanManage(joueurUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(matchStatService.addStat(joueurUserId, body, staffId));
    }

    /** Consultation des stats détaillées d'un joueur (staff/admin). */
    @GetMapping("/staff/stats")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<List<MatchStatResponse>> getPlayerStats(
            @RequestParam Long joueurUserId) {
        ensureStaffCanManage(joueurUserId);
        return ResponseEntity.ok(matchStatService.getStatsOf(joueurUserId));
    }

    /**
     * B.6 — Pose du statut médical APT/INAPTE. Réservé au staff MÉDICAL
     * de la catégorie du joueur (ou ADMIN) — vérifié dans MedicalService.
     */
    @PutMapping("/staff/medical-status")
    @PreAuthorize("hasAnyRole('ENTRAINEUR','STAFF','ADMIN')")
    public ResponseEntity<MedicalResponse> setMedicalStatus(
            @RequestParam Long joueurUserId,
            @RequestBody MedicalStatusRequest body) {
        return ResponseEntity.ok(medicalService.setMedicalStatusAndRespond(
                joueurUserId, body.status(), body.note()));
    }

    // ─────────────────────────── HELPERS ───────────────────────────

    public record ShareDocumentRequest(String title, String url) {
    }

    public record MedicalStatusRequest(
            com.wydad.digital.sports.enums.MedicalStatus status,
            String note) {
    }

    /**
     * Phase 3 — résout l'identité staff courante (0 pour une action
     * administrative), sans cibler de joueur particulier.
     */
    private Long resolveStaffIdentity() {
        if (SportsUserContext.isAdmin()) {
            return 0L;
        }
        Long userId = SportsUserContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        return staffRepository.findByUserId(userId)
                .map(Staff::getId)
                .orElseThrow(() -> new AccessDeniedException("Aucun profil staff lié à votre compte"));
    }

    /**
     * Vérifie que le staff courant encadre la catégorie du joueur visé et
     * renvoie l'id du profil staff (0 pour une action administrative).
     */
    private Long ensureStaffCanManage(Long targetJoueurUserId) {
        if (SportsUserContext.isAdmin()) {
            return 0L; // l'admin passe partout, hors périmètre staff catégorie
        }
        Long userId = SportsUserContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        Staff staff = staffRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Aucun profil staff lié à votre compte"));

        var player = playerSpaceService.getPlayerEntity(targetJoueurUserId);
        boolean sameCategory = staff.getSportType() == player.getSportType()
                && staff.getAssignedCategory() == player.getCategory();
        if (!sameCategory) {
            throw new AccessDeniedException(
                    "Seul le staff encadrant la catégorie du joueur peut agir sur lui");
        }
        return staff.getId();
    }

    /**
     * Phase 5 — lecture des réponses d'une séance : la séance doit appartenir
     * au groupe (sport + catégorie) du staff appelant. Un entraîneur U17
     * Football ne peut donc pas consulter les réponses d'une séance U20
     * Basketball, même authentifié. L'ADMIN passe partout.
     */
    private void ensureStaffOwnsSession(Long sessionId) {
        if (SportsUserContext.isAdmin()) {
            return;
        }
        Long userId = SportsUserContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        Staff staff = staffRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Aucun profil staff lié à votre compte"));
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Séance introuvable"));
        boolean sameGroup = staff.getSportType() == session.getSportType()
                && staff.getAssignedCategory() == session.getCategory();
        if (!sameGroup) {
            throw new AccessDeniedException(
                    "Cette séance n'appartient pas à votre catégorie");
        }
    }
}
