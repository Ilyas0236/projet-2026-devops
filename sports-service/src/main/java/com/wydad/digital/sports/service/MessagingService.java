package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Announcement;
import com.wydad.digital.sports.model.Message;
import com.wydad.digital.sports.repository.AnnouncementRepository;
import com.wydad.digital.sports.repository.MessageRepository;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import com.wydad.digital.sports.filter.SportsUserContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Messagerie joueur ↔ staff et annonces du club (B.5).
 *
 * Règles serveur :
 * <ul>
 *   <li>un JOUEUR n'écrit qu'au staff encadrant SA catégorie ;</li>
 *   <li>un STAFF n'écrit qu'aux joueurs de SA catégorie ;</li>
 *   <li>l'ADMIN écrit à tout le monde ;</li>
 *   <li>une conversation n'est lisible que par ses deux participants.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MessagingService {

    private final MessageRepository messageRepository;
    private final AnnouncementRepository announcementRepository;
    private final PlayerRepository playerRepository;
    private final StaffRepository staffRepository;
    private final NotificationClient notificationClient;

    // ─────────────────────────── MESSAGERIE ───────────────────────────

    /**
     * Envoi d'un message. L'appariement autorisé est vérifié ici
     * (jamais côté client) selon le rôle de l'expéditeur.
     */
    @Transactional
    public Message sendToStaffOrPlayer(Long recipientUserId, String content) {
        Long me = requireCurrentUserId();
        String myRole = SportsUserContext.getCurrentUserRole();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide");
        }
        if (recipientUserId.equals(me)) {
            throw new IllegalArgumentException("Impossible de s'écrire à soi-même");
        }

        if ("JOUEUR".equals(myRole)) {
            // Le destinataire doit être un staff encadrant MA catégorie.
            var player = playerRepository.findByUserId(me)
                    .orElseThrow(() -> new AccessDeniedException(
                            "Aucune fiche sportive liée à votre compte"));
            StaffRef staff = findStaffRef(recipientUserId);
            if (staff == null || staff.sportType() != player.getSportType()
                    || staff.category() != player.getCategory()) {
                throw new AccessDeniedException(
                        "Vous ne pouvez écrire qu'au staff encadrant votre catégorie");
            }
        } else if ("STAFF".equals(myRole)) {
            // Le destinataire doit être un joueur de MA catégorie.
            StaffRef staff = findStaffRef(me);
            if (staff == null) {
                throw new AccessDeniedException("Aucun profil staff lié à votre compte");
            }
            var player = playerRepository.findByUserId(recipientUserId)
                    .orElseThrow(() -> new AccessDeniedException(
                            "Ce destinataire n'est pas un joueur de votre catégorie"));
            if (staff.sportType() != player.getSportType()
                    || staff.category() != player.getCategory()) {
                throw new AccessDeniedException(
                        "Vous ne pouvez écrire qu'aux joueurs de votre catégorie");
            }
        } else if (!"ADMIN".equals(myRole)) {
            throw new AccessDeniedException("Rôle non autorisé pour la messagerie");
        }
        // ADMIN : libre d'écrire à tout le monde.

        String senderName = resolveName(me, myRole);
        Message saved = messageRepository.save(Message.builder()
                .senderUserId(me)
                .senderName(senderName)
                .senderRole(myRole)
                .recipientUserId(recipientUserId)
                .content(content.trim())
                .build());

        notifyRecipient(saved);
        return saved;
    }

    /** Conversation entre l'utilisateur courant et une autre personne. */
    public List<Message> getConversationWith(Long otherUserId) {
        Long me = requireCurrentUserId();
        return messageRepository
                .findBySenderUserIdAndRecipientUserIdOrRecipientUserIdAndSenderUserIdOrderByCreatedAtAsc(
                        me, otherUserId, me, otherUserId);
    }

    /** Boîte de réception : uniquement MES messages reçus. */
    public List<Message> getMyInbox() {
        Long me = requireCurrentUserId();
        return messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(me);
    }

    private void notifyRecipient(Message m) {
        String preview = m.getContent().length() > 80
                ? m.getContent().substring(0, 80) + "…" : m.getContent();
        notificationClient.notifyUser(m.getRecipientUserId(), null,
                "Nouveau message de " + m.getSenderName(),
                preview,
                "/joueur/dashboard");
    }

    private record StaffRef(SportType sportType, Category category) {
    }

    private StaffRef findStaffRef(Long userId) {
        return staffRepository.findByUserId(userId)
                .map(s -> new StaffRef(s.getSportType(), s.getAssignedCategory()))
                .orElse(null);
    }

    private String resolveName(Long userId, String role) {
        if ("JOUEUR".equals(role)) {
            return playerRepository.findByUserId(userId)
                    .map(p -> p.getFullName()).orElse("Joueur");
        }
        return staffRepository.findByUserId(userId)
                .map(s -> s.getFullName()).orElse("Staff");
    }

    // ──────────────────────────── ANNONCES ────────────────────────────

    /**
     * Publication d'une annonce (staff/admin). Ciblage optionnel :
     * sans sport/catégorie l'annonce vaut pour tout le club.
     */
    @Transactional
    public Announcement publish(String title, String body, SportType sportType, Category category) {
        Long me = requireCurrentUserId();
        String myRole = SportsUserContext.getCurrentUserRole();
        if (!"ADMIN".equals(myRole) && !"STAFF".equals(myRole)) {
            throw new AccessDeniedException("Seul le staff ou l'admin peut publier une annonce");
        }
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Titre et contenu obligatoires");
        }
        return announcementRepository.save(Announcement.builder()
                .title(title.trim())
                .body(body.trim())
                .sportType(sportType)
                .category(category)
                .createdByStaffId(me)
                .createdByName(resolveName(me, myRole))
                .build());
    }

    /**
     * Annonces visibles par le connecté : club entier + celles de SA
     * catégorie. Un staff/admin voit aussi celles de sa propre affectation.
     */
    public List<Announcement> getVisibleAnnouncements() {
        Long me = requireCurrentUserId();
        String myRole = SportsUserContext.getCurrentUserRole();

        var clubWide = announcementRepository.findBySportTypeIsNullOrderByCreatedAtDesc();

        List<Announcement> mine;
        if ("JOUEUR".equals(myRole)) {
            var p = playerRepository.findByUserId(me).orElse(null);
            mine = p == null ? List.of() : announcementRepository
                    .findBySportTypeAndCategoryOrderByCreatedAtDesc(p.getSportType(), p.getCategory());
        } else {
            var s = staffRepository.findByUserId(me).orElse(null);
            mine = s == null ? List.of() : announcementRepository
                    .findBySportTypeAndCategoryOrderByCreatedAtDesc(
                            s.getSportType(), s.getAssignedCategory());
        }

        var all = new java.util.ArrayList<>(clubWide);
        all.addAll(mine);
        all.sort(java.util.Comparator.comparing(Announcement::getCreatedAt).reversed());
        return all;
    }

    private Long requireCurrentUserId() {
        Long id = SportsUserContext.getCurrentUserId();
        if (id == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        return id;
    }
}
