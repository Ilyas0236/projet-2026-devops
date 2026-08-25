package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.AuthClient;
import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.ScheduledCall;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.ScheduledCallRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 5 — appels vidéo/vocaux programmés.
 *
 * Règles d'autorisation (décidées côté serveur, jamais par le client) :
 * <ul>
 *   <li><b>ENTRAINEUR</b> : programme pour SA catégorie (fiche staff
 *       obligatoire, sport/catégorie forcés depuis la fiche) ; audience =
 *       joueurs et/ou staff de la catégorie ;</li>
 *   <li><b>PRESIDENT</b> : programme pour toute catégorie, les adhérents
 *       PREMIUM ou une liste explicite d'utilisateurs ;</li>
 *   <li><b>ADMIN</b> : tout ;</li>
 *   <li><b>JOUEUR / autres</b> : ne peuvent QUE lister leurs appels et
 *       rejoindre (jeton) — jamais créer.</li>
 * </ul>
 * Les participants forment une liste fermée : hors de cette liste, pas de
 * jeton LiveKit, donc pas d'accès média.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledCallService {

    private final ScheduledCallRepository callRepository;
    private final PlayerRepository playerRepository;
    private final StaffRepository staffRepository;
    private final AuthClient authClient;
    private final NotificationClient notificationClient;
    private final LiveKitTokenService liveKitTokenService;

    /** URL du serveur LiveKit (wss://…), retournée au client avec le jeton. */
    @org.springframework.beans.factory.annotation.Value("${livekit.url:}")
    private String liveKitUrl;

    /** Durée de vie d'un jeton de connexion (le client le redemande à chaque join). */
    private static final long TOKEN_TTL_SECONDS = 3600;

    // ───────────────────────────── CRÉATION ─────────────────────────────

    @Transactional
    public ScheduledCall createCall(CreateCallRequest req) {
        Long me = requireCurrentUserId();
        String role = requireRole();

        if (!"ENTRAINEUR".equals(role) && !"PRESIDENT".equals(role) && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("Seuls l'entraîneur et le président peuvent programmer un appel");
        }
        if (req.title() == null || req.title().isBlank() || req.title().trim().length() > 120) {
            throw new IllegalArgumentException("Titre requis (120 caractères maximum)");
        }
        if (req.scheduledAt() != null && req.scheduledAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new IllegalArgumentException("La date de l'appel doit être dans le futur");
        }
        if (req.durationMinutes() != null && (req.durationMinutes() < 5 || req.durationMinutes() > 240)) {
            throw new IllegalArgumentException("Durée entre 5 et 240 minutes");
        }

        // Entraîneur : la catégorie vient de SA fiche (le client ne choisit pas).
        SportType sportType = req.sportType();
        Category category = req.category();
        if ("ENTRAINEUR".equals(role)) {
            Staff fiche = staffRepository.findByUserId(me)
                    .orElseThrow(() -> new AccessDeniedException("Aucun profil staff lié à votre compte"));
            sportType = fiche.getSportType();
            category = fiche.getAssignedCategory();
        }

        Set<Long> participants = resolveAudience(role, req, sportType, category);
        participants.add(me); // l'organisateur participe toujours

        ScheduledCall call = callRepository.save(ScheduledCall.builder()
                .title(req.title().trim())
                .roomName("wac-call-" + java.util.UUID.randomUUID().toString().substring(0, 12))
                .sportType(sportType)
                .category(category)
                .organizerUserId(me)
                .organizerName(resolveOrganizerName(me, role))
                .organizerRole(role)
                .scheduledAt(req.scheduledAt())
                .durationMinutes(req.durationMinutes())
                .participantUserIds(participants)
                .build());

        notifyParticipants(call);
        log.info("Appel {} programmé par {} ({}) - {} participant(s)",
                call.getId(), me, role, participants.size());
        return call;
    }

    /**
     * Résolution de l'audience selon la cible demandée. L'ENTRAINEUR ne peut
     * cibler que sa catégorie ; le PRESIDENT/ADMIN ont en plus PREMIUM et
     * liste explicite.
     */
    private Set<Long> resolveAudience(String role, CreateCallRequest req,
                                      SportType sportType, Category category) {
        TargetType target = req.target() == null ? TargetType.CATEGORIE_EQUIPE : req.target();
        Set<Long> ids = new HashSet<>();

        switch (target) {
            case CATEGORIE_JOUEURS -> {
                requireCategory(sportType, category);
                for (Player p : playerRepository.findBySportTypeAndCategory(sportType, category)) {
                    ids.add(p.getUserId());
                }
            }
            case CATEGORIE_EQUIPE -> {
                requireCategory(sportType, category);
                for (Player p : playerRepository.findBySportTypeAndCategory(sportType, category)) {
                    ids.add(p.getUserId());
                }
                for (Staff s : staffRepository.findBySportTypeAndAssignedCategory(sportType, category)) {
                    ids.add(s.getUserId());
                }
            }
            case PREMIUM -> {
                if (!"PRESIDENT".equals(role) && !"ADMIN".equals(role)) {
                    throw new AccessDeniedException("Cible réservée au président");
                }
                for (AuthClient.UserProfile u : authClient.getAllActiveUsers()) {
                    if (u.isValide() && "ADHERENT".equals(u.role()) && "PREMIUM".equals(u.membershipLevel())) {
                        ids.add(u.id());
                    }
                }
            }
            case UTILISATEURS -> {
                if (!"PRESIDENT".equals(role) && !"ADMIN".equals(role)) {
                    throw new AccessDeniedException("Cible réservée au président");
                }
                if (req.targetUserIds() == null || req.targetUserIds().isEmpty()) {
                    throw new IllegalArgumentException("Liste de destinataires vide");
                }
                Set<Long> known = new HashSet<>();
                for (AuthClient.UserProfile u : authClient.getAllActiveUsers()) {
                    if (u.isValide()) known.add(u.id());
                }
                for (Long id : req.targetUserIds()) {
                    if (id != null && known.contains(id)) ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Aucun destinataire trouvé pour cette cible");
        }
        return ids;
    }

    private void requireCategory(SportType sportType, Category category) {
        if (sportType == null || category == null) {
            throw new IllegalArgumentException("Sport et catégorie requis pour cette cible");
        }
    }

    // ───────────────────────────── LECTURE ─────────────────────────────

    /** Agenda de l'utilisateur : appels où il est organisateur OU participant. */
    @Transactional(readOnly = true)
    public List<ScheduledCall> getMyCalls() {
        Long me = requireCurrentUserId();
        return callRepository
                .findByParticipantUserIdsContainingOrOrganizerUserIdOrderByScheduledAtDesc(me, me);
    }

    // ───────────────────────────── JETON ─────────────────────────────

    /**
     * Jeton LiveKit pour rejoindre l'appel. Refus si l'appelant n'est ni
     * organisateur ni participant (liste fermée), ou si l'appel est annulé.
     */
    @Transactional(readOnly = true)
    public CallToken joinToken(Long callId) {
        Long me = requireCurrentUserId();
        ScheduledCall call = callRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Appel introuvable"));

        boolean organizer = call.getOrganizerUserId().equals(me);
        boolean participant = call.getParticipantUserIds().contains(me);
        if (!organizer && !participant) {
            throw new AccessDeniedException("Vous n'êtes pas convié à cet appel");
        }
        if (call.getStatus() == ScheduledCall.CallStatus.ANNULE) {
            throw new IllegalStateException("Cet appel a été annulé");
        }

        String token = liveKitTokenService.createToken(
                call.getRoomName(), me, displayNameFor(me, call), organizer, TOKEN_TTL_SECONDS);
        return new CallToken(call.getId(), call.getRoomName(), token, liveKitUrl(), organizer);
    }

    public boolean isLiveKitConfigured() {
        return liveKitTokenService.isConfigured();
    }

    private String liveKitUrl() {
        return liveKitUrl;
    }

    // ───────────────────────────── ANNULATION ─────────────────────────────

    @Transactional
    public ScheduledCall cancelCall(Long callId) {
        Long me = requireCurrentUserId();
        String role = requireRole();
        ScheduledCall call = callRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Appel introuvable"));

        if (!call.getOrganizerUserId().equals(me) && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("Seul l'organisateur peut annuler cet appel");
        }
        if (call.getStatus() == ScheduledCall.CallStatus.TERMINE) {
            throw new IllegalStateException("Cet appel est déjà terminé");
        }
        call.setStatus(ScheduledCall.CallStatus.ANNULE);
        return callRepository.save(call);
    }

    // ───────────────────────────── HELPERS ─────────────────────────────

    private void notifyParticipants(ScheduledCall call) {
        try {
            String quand = call.getScheduledAt() != null
                    ? call.getScheduledAt().toLocalDate() + " " + call.getScheduledAt().toLocalTime()
                    : "immédiatement";
            for (Long userId : call.getParticipantUserIds()) {
                if (userId.equals(call.getOrganizerUserId())) continue;
                boolean isPlayer = playerRepository.findByUserId(userId).isPresent();
                notificationClient.notifyUser(userId, null,
                        "Appel programmé : " + call.getTitle(),
                        "Par " + call.getOrganizerName() + " · " + quand,
                        isPlayer ? "/joueur/dashboard" : "/espace-staff/dashboard");
            }
        } catch (Exception e) {
            log.warn("Notifications d'appel non envoyées: {}", e.getMessage());
        }
    }

    private String displayNameFor(Long userId, ScheduledCall call) {
        if (call.getOrganizerUserId().equals(userId)) return call.getOrganizerName();
        return playerRepository.findByUserId(userId).map(Player::getFullName)
                .or(() -> staffRepository.findByUserId(userId).map(Staff::getFullName))
                .orElse("User-" + userId);
    }

    private String resolveOrganizerName(Long userId, String role) {
        if ("PRESIDENT".equals(role)) return "Président";
        return staffRepository.findByUserId(userId).map(Staff::getFullName).orElse("Organisateur");
    }

    private Long requireCurrentUserId() {
        Long id = SportsUserContext.getCurrentUserId();
        if (id == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        return id;
    }

    private String requireRole() {
        String role = SportsUserContext.getCurrentUserRole();
        if (role == null) {
            throw new AccessDeniedException("Rôle introuvable dans le contexte de sécurité");
        }
        return role;
    }

    // ───────────────────────────── DTO ─────────────────────────────

    /** Cible de l'appel. */
    public enum TargetType { CATEGORIE_JOUEURS, CATEGORIE_EQUIPE, PREMIUM, UTILISATEURS }

    public record CreateCallRequest(
            String title,
            SportType sportType,
            Category category,
            LocalDateTime scheduledAt,
            Integer durationMinutes,
            TargetType target,
            Set<Long> targetUserIds) {
    }

    /** Réponse « rejoindre » : tout ce que le SDK LiveKit client nécessite. */
    public record CallToken(Long callId, String roomName, String token, String url, boolean organizer) {
    }
}
