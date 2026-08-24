package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.model.TeamMessage;
import com.wydad.digital.sports.repository.MessageRepository;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import com.wydad.digital.sports.repository.TeamMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 4 — messagerie de GROUPE « WhatsApp » (texte uniquement).
 *
 * Le groupe est « Équipe {sportType} {category} » : tous les joueurs de la
 * catégorie + tout le staff encadrant. L'adhésion est déduite de la fiche
 * (player/staff) et non stockée — impossible de s'inviter dans un groupe.
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
    private final PlayerRepository playerRepository;
    private final StaffRepository staffRepository;
    private final NotificationClient notificationClient;

    /** Nombre de membres du groupe au-delà duquel on n'envoie plus de notifs individuelles. */
    private static final int MAX_NOTIFICATIONS = 30;

    /**
     * Enregistre un message de groupe après vérification d'adhésion.
     *
     * @return le message persisté (diffusion ensuite par le canal appelant)
     */
    public TeamMessage sendToGroup(SportType sportType, Category category, String content) {
        Long me = requireCurrentUserId();
        String myRole = SportsUserContext.getCurrentUserRole();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide");
        }
        if (content.trim().length() > 500) {
            throw new IllegalArgumentException("Message trop long (maximum 500 caractères)");
        }
        requireMembership(sportType, category, me, myRole);

        String senderName = resolveName(me, myRole);
        return teamMessageRepository.save(TeamMessage.builder()
                .sportType(sportType)
                .category(category)
                .senderUserId(me)
                .senderName(senderName)
                .senderRole(myRole != null ? myRole : "INCONNU")
                .content(content.trim())
                .build());
    }

    /** Historique récent d'un groupe (les 100 derniers, ordre chronologique). */
    public List<TeamMessage> getRecentMessages(SportType sportType, Category category) {
        Long me = requireCurrentUserId();
        requireMembership(sportType, category, me, SportsUserContext.getCurrentUserRole());
        List<TeamMessage> latestDesc = teamMessageRepository
                .findBySportTypeAndCategoryOrderByCreatedAtDesc(sportType, category,
                        PageRequest.of(0, 100));
        // Remet en ordre chronologique pour l'affichage chat.
        java.util.Collections.reverse(latestDesc);
        return latestDesc;
    }

    /** Liste des membres du groupe (joueurs + staff), pour l'en-tête du chat. */
    public List<GroupMember> getMembers(SportType sportType, Category category) {
        Long me = requireCurrentUserId();
        requireMembership(sportType, category, me, SportsUserContext.getCurrentUserRole());

        List<GroupMember> members = new java.util.ArrayList<>();
        for (Player p : playerRepository.findBySportTypeAndCategory(sportType, category)) {
            members.add(new GroupMember(p.getUserId(), p.getFullName(), "JOUEUR"));
        }
        for (Staff s : staffRepository.findBySportTypeAndAssignedCategory(sportType, category)) {
            members.add(new GroupMember(s.getUserId(), s.getFullName(),
                    s.getRole() != null ? s.getRole().name() : "STAFF"));
        }
        return members;
    }

    // ───────────────────────────── HELPERS ─────────────────────────────

    /**
     * Adhésion au groupe : la fiche du connecté (joueur ou staff) doit
     * correspondre au sport/catégorie demandés. L'ADMIN passe partout
     * (supervision), comme pour la messagerie privée.
     */
    private void requireMembership(SportType sportType, Category category, Long userId, String role) {
        if ("ADMIN".equals(role)) { return; }
        if ("JOUEUR".equals(role)) {
            Player p = playerRepository.findByUserId(userId)
                    .orElseThrow(() -> new AccessDeniedException(
                            "Aucune fiche sportive liée à votre compte"));
            if (p.getSportType() != sportType || p.getCategory() != category) {
                throw new AccessDeniedException("Ce groupe ne correspond pas à votre équipe");
            }
            return;
        }
        // STAFF / ENTRAINEUR / autres rôles staff : fiche obligatoire + catégorie matchante.
        Staff s = staffRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Aucun profil staff lié à votre compte"));
        if (s.getSportType() != sportType || s.getAssignedCategory() != category) {
            throw new AccessDeniedException("Ce groupe ne correspond pas à votre équipe");
        }
    }

    private String resolveName(Long userId, String role) {
        if ("JOUEUR".equals(role)) {
            return playerRepository.findByUserId(userId).map(Player::getFullName).orElse("Joueur");
        }
        return staffRepository.findByUserId(userId).map(Staff::getFullName).orElse("Staff");
    }

    private Long requireCurrentUserId() {
        Long id = SportsUserContext.getCurrentUserId();
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

            for (Player p : playerRepository.findBySportTypeAndCategory(
                    message.getSportType(), message.getCategory())) {
                if (!online.contains(p.getUserId()) && !p.getUserId().equals(message.getSenderUserId())
                        && sent < MAX_NOTIFICATIONS) {
                    notificationClient.notifyUser(p.getUserId(), null,
                            message.getSenderName() + " · Groupe équipe", preview,
                            "/joueur/dashboard");
                    sent++;
                }
            }
            for (Staff st : staffRepository.findBySportTypeAndAssignedCategory(
                    message.getSportType(), message.getCategory())) {
                if (!online.contains(st.getUserId())
                        && !st.getUserId().equals(message.getSenderUserId())
                        && sent < MAX_NOTIFICATIONS) {
                    notificationClient.notifyUser(st.getUserId(), null,
                            message.getSenderName() + " · Groupe équipe", preview,
                            "/espace-staff/dashboard");
                    sent++;
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TeamChatService.class).warn(
                    "Notifications hors ligne non envoyées: {}", e.getMessage());
        }
    }

    /** Membre du groupe (en-tête du chat). */
    public record GroupMember(Long userId, String fullName, String role) {
    }
}
