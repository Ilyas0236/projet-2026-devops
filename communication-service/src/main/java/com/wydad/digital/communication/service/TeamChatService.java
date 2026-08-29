package com.wydad.digital.communication.service;

import com.wydad.digital.communication.client.NotificationClient;
import com.wydad.digital.communication.client.RosterClient;
import com.wydad.digital.communication.filter.UserContext;
import com.wydad.digital.communication.util.TargetUrlResolver;
import com.wydad.digital.communication.model.TeamMessage;
import com.wydad.digital.communication.repository.TeamMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 4 — messagerie de GROUPE « WhatsApp » (texte uniquement).
 *
 * Le groupe est « Équipe {sportType} {category} » : tous les joueurs de la
 * catégorie + tout le staff encadrant. L'adhésion est déduite du roster
 * interrogé sur sports-service ({@link RosterClient}) et non stockée —
 * impossible de s'inviter dans un groupe, et un membre parti de l'équipe
 * perd instantanément l'accès.
 *
 * Deux canaux d'envoi :
 * <ul>
 *   <li><b>WebSocket STOMP</b> (temps réel) : le contrôleur WS appelle
 *       {@link #sendToGroup} puis diffuse ;</li>
 *   <li><b>REST</b> (fallback si le socket est coupé) : même logique.</li>
 * </ul>
 * Dans les deux cas les mêmes règles serveur s'appliquent — jamais côté
 * client.
 */
@Service
@RequiredArgsConstructor
public class TeamChatService {

    private final TeamMessageRepository teamMessageRepository;
    private final RosterClient rosterClient;
    private final NotificationClient notificationClient;

    /** Nombre de membres du groupe au-delà duquel on n'envoie plus de notifs individuelles. */
    private static final int MAX_NOTIFICATIONS = 30;

    /**
     * Enregistre un message de groupe après vérification d'adhésion.
     *
     * @return le message persisté (diffusion ensuite par le canal appelant)
     */
    public TeamMessage sendToGroup(String sportType, String category, String content) {
        return sendToGroupInternal(sportType, category, content, null, null);
    }

    /**
     * Variante avec pièce jointe (photo, etc.). Le contenu texte reste
     * obligatoire : WhatsApp refuse un message « vide + image » et l'UI
     * a besoin d'un aperçu. Si mediaUrl est fourni, content sert de
     * légende (peut être une chaîne courte).
     */
    public TeamMessage sendToGroupWithMedia(String sportType, String category,
                                            String content, String mediaUrl, String mediaType) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new IllegalArgumentException("URL de média obligatoire pour un message avec pièce jointe");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("Type de média obligatoire (IMAGE/VIDEO/AUDIO/FILE)");
        }
        return sendToGroupInternal(sportType, category, content, mediaUrl, mediaType.toUpperCase());
    }

    private TeamMessage sendToGroupInternal(String sportType, String category,
                                            String content, String mediaUrl, String mediaType) {
        Long me = requireCurrentUserId();
        String myRole = UserContext.getCurrentUserRole();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide");
        }
        if (content.trim().length() > 500) {
            throw new IllegalArgumentException("Message trop long (maximum 500 caractères)");
        }
        requireMembership(sportType, category, me, myRole);

        return teamMessageRepository.save(TeamMessage.builder()
                .sportType(sportType.toUpperCase())
                .category(category.toUpperCase())
                .senderUserId(me)
                .senderName(resolveName(me))
                .senderRole(myRole != null ? myRole : "INCONNU")
                .content(content.trim())
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .build());
    }

    /** Historique récent d'un groupe (les 100 derniers, ordre chronologique). */
    public List<TeamMessage> getRecentMessages(String sportType, String category) {
        Long me = requireCurrentUserId();
        requireMembership(sportType, category, me, UserContext.getCurrentUserRole());
        List<TeamMessage> latestDesc = teamMessageRepository
                .findBySportTypeAndCategoryOrderByCreatedAtDesc(sportType.toUpperCase(),
                        category.toUpperCase(), PageRequest.of(0, 100));
        // Remet en ordre chronologique pour l'affichage chat.
        java.util.Collections.reverse(latestDesc);
        return latestDesc;
    }

    /** Liste des membres du groupe (joueurs + staff), pour l'en-tête du chat. */
    public List<RosterClient.RosterMember> getMembers(String sportType, String category) {
        Long me = requireCurrentUserId();
        requireMembership(sportType, category, me, UserContext.getCurrentUserRole());
        return rosterClient.findGroupMembers(sportType.toUpperCase(), category.toUpperCase());
    }

    // ───────────────────────────── HELPERS ─────────────────────────────

    /**
     * Adhésion au groupe :
     * <ul>
     *   <li>ADMIN : passe partout (supervision) ;</li>
     *   <li>PRESIDENT : passe partout — il a un rôle institutionnel de
     *       communication transversale avec n'importe quelle équipe
     *       (cf. exigence métier) ;</li>
     *   <li>JOUEUR/STAFF/ENTRAINEUR : la fiche roster du connecté doit
     *       correspondre au sport/catégorie demandés.</li>
     * </ul>
     */
    private void requireMembership(String sportType, String category, Long userId, String role) {
        if ("ADMIN".equals(role) || "PRESIDENT".equals(role)) {
            return;
        }
        RosterClient.MembershipInfo mine = rosterClient.findMembership(userId);
        if (mine == null) {
            throw new AccessDeniedException(
                    "Aucune fiche sportive ou staff liée à votre compte");
        }
        boolean isPlayerOrStaff = "JOUEUR".equals(mine.rosterRole())
                || "STAFF".equals(mine.rosterRole());
        if (!isPlayerOrStaff || !sportType.equalsIgnoreCase(mine.sportType())
                || !category.equalsIgnoreCase(mine.category())) {
            throw new AccessDeniedException("Ce groupe ne correspond pas à votre équipe");
        }
    }

    /**
     * Nom affiché de l'expéditeur : fiche roster si elle existe, sinon
     * « Administration » pour l'ADMIN (superviseur sans fiche sportive),
     * « Membre » en dernier recours.
     */
    private String resolveName(Long userId) {
        RosterClient.MembershipInfo info = rosterClient.findMembership(userId);
        if (info != null && info.fullName() != null && !info.fullName().isBlank()) {
            return info.fullName();
        }
        return "ADMIN".equals(UserContext.getCurrentUserRole())
                ? "Administration" : "Membre";
    }

    private Long requireCurrentUserId() {
        Long id = UserContext.getCurrentUserId();
        if (id == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        return id;
    }

    // ─────────────── Notification hors ligne (best-effort) ───────────────

    /**
     * Notifie les membres ABSENTS du groupe (hors ligne sur le socket).
     * Appelé par le contrôleur WebSocket APRÈS diffusion temps réel —
     * seuls ceux qui n'ont pas reçu en direct reçoivent une notif in-app.
     */
    public void notifyOfflineMembers(TeamMessage message, Iterable<Long> onlineUserIds) {
        try {
            var online = new java.util.HashSet<Long>();
            if (onlineUserIds != null) {
                onlineUserIds.forEach(online::add);
            }
            int sent = 0;
            String preview = message.getContent().length() > 80
                    ? message.getContent().substring(0, 80) + "…" : message.getContent();

            for (RosterClient.RosterMember member : rosterClient.findGroupMembers(
                    message.getSportType(), message.getCategory())) {
                if (!online.contains(member.userId())
                        && !member.userId().equals(message.getSenderUserId())
                        && sent < MAX_NOTIFICATIONS) {
                    // Quality-final — targetUrl dépend du rôle roster du membre :
                    // JOUEUR → /joueur/dashboard, STAFF/ENTRAINEUR → dashboard staff.
                    String targetUrl = "JOUEUR".equalsIgnoreCase(member.rosterRole())
                            ? TargetUrlResolver.resolve("JOUEUR", "/joueur/dashboard")
                            : TargetUrlResolver.resolve("STAFF",  "/staff/dashboard");
                    notificationClient.notifyUser(member.userId(), null,
                            message.getSenderName() + " · Groupe équipe", preview,
                            targetUrl);
                    sent++;
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TeamChatService.class).warn(
                    "Notifications hors ligne non envoyées: {}", e.getMessage());
        }
    }
}
