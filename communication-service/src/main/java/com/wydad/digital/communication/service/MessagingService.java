package com.wydad.digital.communication.service;

import com.wydad.digital.communication.client.NotificationClient;
import com.wydad.digital.communication.client.RosterClient;
import com.wydad.digital.communication.filter.UserContext;
import com.wydad.digital.communication.util.TargetUrlResolver;
import com.wydad.digital.communication.model.Announcement;
import com.wydad.digital.communication.model.Message;
import com.wydad.digital.communication.repository.AnnouncementRepository;
import com.wydad.digital.communication.repository.MessageRepository;
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
 *   <li>un JOUEUR écrit au staff encadrant SA catégorie ET aux coéquipiers
 *       de SON groupe (même sport+catégorie) ;</li>
 *   <li>un STAFF ou ENTRAINEUR n'écrit qu'aux joueurs de SA catégorie ;</li>
 *   <li>l'ADMIN et le PRESIDENT écrivent à tout le monde ;</li>
 *   <li>une conversation n'est lisible que par ses deux participants.</li>
 * </ul>
 *
 * L'appartenance sport/catégorie n'est pas stockée ici : elle est
 * interrogée sur sports-service via {@link RosterClient} (API interne) —
 * communication-service ne connaît pas les tables players/staff.
 */
@Service
@RequiredArgsConstructor
public class MessagingService {

    private final MessageRepository messageRepository;
    private final AnnouncementRepository announcementRepository;
    private final RosterClient rosterClient;
    private final NotificationClient notificationClient;
    private final MessageMediaService messageMediaService;

    /** Pièce jointe optionnelle (V2.3). Tous champs nullables. */
    public record Attachment(
            String publicId,
            String secureUrl,
            String resourceType,
            String fileName,
            Long sizeBytes) {}

    // ─────────────────────────── MESSAGERIE ───────────────────────────

    /** Surcharge rétro-compatible sans pièce jointe. */
    @Transactional
    public Message sendToStaffOrPlayer(Long recipientUserId, String content) {
        return sendToStaffOrPlayer(recipientUserId, content, null);
    }

    /**
     * Envoi d'un message. L'appariement autorisé est vérifié ici
     * (jamais côté client) selon le rôle de l'expéditeur.
     *
     * <p>Une pièce jointe est optionnelle : si {@code content} est vide
     * mais qu'une {@code attachment} est fournie, on accepte (V2.3 — cas
     * du partage de photo sans légende).</p>
     */
    @Transactional
    public Message sendToStaffOrPlayer(Long recipientUserId, String content, Attachment attachment) {
        Long me = requireCurrentUserId();
        String myRole = UserContext.getCurrentUserRole();
        boolean hasContent = content != null && !content.isBlank();
        boolean hasAttachment = attachment != null && attachment.publicId() != null
                && !attachment.publicId().isBlank();
        if (!hasContent && !hasAttachment) {
            throw new IllegalArgumentException("Le message et la pièce jointe sont tous deux vides");
        }
        String trimmedContent = hasContent ? content.trim() : "";
        if (recipientUserId.equals(me)) {
            throw new IllegalArgumentException("Impossible de s'écrire à soi-même");
        }

        RosterClient.MembershipInfo mine = rosterClient.findMembership(me);
        if ("JOUEUR".equals(myRole)) {
            // Destinataire : un staff encadrant MA catégorie, ou un
            // coéquipier de MON groupe (même sport+catégorie).
            if (mine == null) {
                throw new AccessDeniedException("Aucune fiche sportive liée à votre compte");
            }
            RosterClient.MembershipInfo theirs = rosterClient.findMembership(recipientUserId);
            boolean recipientIsStaff =
                    theirs != null && "STAFF".equals(theirs.rosterRole());
            boolean recipientIsTeammate =
                    theirs != null && "JOUEUR".equals(theirs.rosterRole());
            if ((!recipientIsStaff && !recipientIsTeammate) || !sameGroup(mine, theirs)) {
                throw new AccessDeniedException(
                        "Vous ne pouvez écrire qu'au staff encadrant votre catégorie "
                                + "et aux joueurs de votre équipe");
            }
        } else if ("STAFF".equals(myRole) || "ENTRAINEUR".equals(myRole)) {
            // Le destinataire doit être un joueur de MA catégorie.
            // L'ENTRAINEUR possède une fiche roster interne de rôle STAFF.
            if (mine == null || !"STAFF".equals(mine.rosterRole())) {
                throw new AccessDeniedException("Aucun profil staff lié à votre compte");
            }
            RosterClient.MembershipInfo theirs = rosterClient.findMembership(recipientUserId);
            boolean recipientIsPlayer = theirs != null && "JOUEUR".equals(theirs.rosterRole());
            if (!recipientIsPlayer || !sameGroup(mine, theirs)) {
                throw new AccessDeniedException(
                        "Vous ne pouvez écrire qu'aux joueurs de votre catégorie");
            }
        } else if ("PRESIDENT".equals(myRole)) {
            // Le Président écrit aux agents (staff) et joueurs du club —
            // sans restriction de groupe, comme l'ADMIN.
        } else if (!"ADMIN".equals(myRole)) {
            throw new AccessDeniedException("Rôle non autorisé pour la messagerie");
        }
        // ADMIN et PRESIDENT : libres d'écrire à tout le monde.

        Message saved = messageRepository.save(Message.builder()
                .senderUserId(me)
                .senderName(resolveName(me, myRole, mine))
                .senderRole(myRole)
                .recipientUserId(recipientUserId)
                .content(trimmedContent)
                .attachmentPublicId(hasAttachment ? attachment.publicId() : null)
                .attachmentSecureUrl(hasAttachment ? attachment.secureUrl() : null)
                .attachmentResourceType(hasAttachment ? attachment.resourceType() : null)
                .attachmentFileName(hasAttachment ? attachment.fileName() : null)
                .attachmentSizeBytes(hasAttachment ? attachment.sizeBytes() : null)
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

    // ──────────────────────────── ANNONCES ────────────────────────────

    /**
     * Publication d'une annonce (staff/admin). Ciblage optionnel :
     * sans sport/catégorie l'annonce vaut pour tout le club. Un STAFF ne
     * peut cibler que SA propre catégorie — jamais celle d'un autre.
     */
    @Transactional
    public Announcement publish(String title, String body,
                                String sportType, String category) {
        Long me = requireCurrentUserId();
        String myRole = UserContext.getCurrentUserRole();
        if (!"ADMIN".equals(myRole) && !"STAFF".equals(myRole)) {
            throw new AccessDeniedException("Seul le staff ou l'admin peut publier une annonce");
        }
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Titre et contenu obligatoires");
        }

        String targetSport = sportType;
        String targetCategory = category;
        RosterClient.MembershipInfo mine = rosterClient.findMembership(me);
        if ("STAFF".equals(myRole)) {
            // Ciblage imposé : le staff publie pour SON groupe uniquement.
            if (mine == null || !"STAFF".equals(mine.rosterRole())) {
                throw new AccessDeniedException("Aucun profil staff lié à votre compte");
            }
            if ((sportType != null && !sportType.equalsIgnoreCase(mine.sportType()))
                    || (category != null && !category.equalsIgnoreCase(mine.category()))) {
                throw new AccessDeniedException(
                        "Vous ne pouvez publier que pour votre propre catégorie");
            }
            targetSport = mine.sportType();
            targetCategory = mine.category();
        }

        return announcementRepository.save(Announcement.builder()
                .title(title.trim())
                .body(body.trim())
                .sportType(targetSport)
                .category(targetCategory)
                .createdByStaffId(me)
                .createdByName(resolveName(me, myRole, mine))
                .build());
    }

    /**
     * Annonces visibles par le connecté : club entier + celles de SA
     * catégorie (déduite du roster). L'ADMIN voit les annonces club ; un
     * utilisateur sans fiche roster ne voit que les annonces club.
     */
    public List<Announcement> getVisibleAnnouncements() {
        Long me = requireCurrentUserId();

        List<Announcement> all = new java.util.ArrayList<>(
                announcementRepository.findBySportTypeIsNullOrderByCreatedAtDesc());

        RosterClient.MembershipInfo mine = rosterClient.findMembership(me);
        if (mine != null && mine.sportType() != null && mine.category() != null) {
            all.addAll(announcementRepository.findBySportTypeAndCategoryOrderByCreatedAtDesc(
                    mine.sportType(), mine.category()));
        }
        all.sort(java.util.Comparator.comparing(Announcement::getCreatedAt).reversed());
        return all;
    }

    // ───────────────────────────── HELPERS ─────────────────────────────

    /** Même sport/catégorie (comparaison insensible à la casse — STRING). */
    private boolean sameGroup(RosterClient.MembershipInfo a, RosterClient.MembershipInfo b) {
        return a.sportType() != null && b.sportType() != null
                && a.category() != null && b.category() != null
                && a.sportType().equalsIgnoreCase(b.sportType())
                && a.category().equalsIgnoreCase(b.category());
    }

    /** Nom d'affichage : fiche roster si disponible, sinon fallback rôle. */
    private String resolveName(Long userId, String role, RosterClient.MembershipInfo info) {
        if (info != null && info.fullName() != null && !info.fullName().isBlank()) {
            return info.fullName();
        }
        if ("ADMIN".equals(role)) {
            return "Administration";
        }
        return "PRESIDENT".equals(role) ? "Présidence" : "Membre";
    }

    private void notifyRecipient(Message m) {
        String preview = m.getContent().length() > 80
                ? m.getContent().substring(0, 80) + "…" : m.getContent();
        // Quality-final — targetUrl dépend du rôle du destinataire (pas
        // toujours un joueur). On déduit le rôle du senderName du message
        // (heuristique) ; à terme, le front résoudra côté cloche.
        notificationClient.notifyUser(m.getRecipientUserId(), null,
                "Nouveau message de " + m.getSenderName(),
                preview,
                TargetUrlResolver.resolveFromCurrentContext("/messagerie"));
    }

    private Long requireCurrentUserId() {
        Long id = UserContext.getCurrentUserId();
        if (id == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        return id;
    }
}
