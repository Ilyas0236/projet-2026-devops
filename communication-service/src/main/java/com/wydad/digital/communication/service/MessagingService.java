package com.wydad.digital.communication.service;

import com.wydad.digital.communication.client.AuthUserInfoClient;
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
    private final AuthUserInfoClient authUserInfoClient;
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
            // Destinataire : un staff encadrant MA catégorie, un coéquipier
            // de MON groupe (même sport+catégorie), OU le président de MA
            // discipline (C.21 — le joueur doit pouvoir interpeller le
            // président de sa section, pas uniquement le staff).
            if (mine == null) {
                throw new AccessDeniedException("Aucune fiche sportive liée à votre compte");
            }
            RosterClient.MembershipInfo theirs = rosterClient.findMembership(recipientUserId);
            boolean recipientIsStaff =
                    theirs != null && "STAFF".equals(theirs.rosterRole());
            boolean recipientIsTeammate =
                    theirs != null && "JOUEUR".equals(theirs.rosterRole());
            // C.21 — récupération du rôle destinataire via UserContext n'est
            // pas dispo ici (RosterClient ne lit que player/staff). On
            // détermine « président de la même discipline » en comparant
            // sportType du joueur au sportType des fiches du destinataire.
            // Si le destinataire est le président de MA discipline → OK.
            boolean recipientIsPresident = theirs == null && isPresidentOfMyDiscipline(
                    mine, recipientUserId);
            boolean ok = (recipientIsStaff || recipientIsTeammate || recipientIsPresident)
                    && (theirs == null || sameGroup(mine, theirs));
            if (!ok) {
                throw new AccessDeniedException(
                        "Vous ne pouvez écrire qu'au staff encadrant votre catégorie, "
                                + "aux joueurs de votre équipe, ou au président de votre discipline");
            }
        } else if ("STAFF".equals(myRole) || "ENTRAINEUR".equals(myRole)) {
            // Le destinataire doit être un joueur de MA catégorie OU un autre
            // membre encadrant de MA discipline (autre STAFF ou ENTRAINEUR —
            // coordination entre entraîneurs de la même section).
            // L'ENTRAINEUR possède une fiche roster interne de rôle STAFF.
            if (mine == null || !"STAFF".equals(mine.rosterRole())) {
                throw new AccessDeniedException("Aucun profil staff lié à votre compte");
            }
            RosterClient.MembershipInfo theirs = rosterClient.findMembership(recipientUserId);

            // Cas 1 : destinataire = joueur de MA catégorie (existant)
            if (theirs != null && "JOUEUR".equals(theirs.rosterRole())) {
                if (!sameGroup(mine, theirs)) {
                    throw new AccessDeniedException(
                            "Vous ne pouvez écrire qu'aux joueurs de votre catégorie");
                }
                // OK — joueur de ma catégorie
            } else if (theirs != null && "STAFF".equals(theirs.rosterRole())) {
                // V2.5.bis — destinataire = autre membre encadrant (STAFF ou
                // ENTRAINEUR, ce dernier ayant une fiche roster STAFF). On
                // n'exige que la même discipline (la catégorie est souvent
                // null sur les fiches encadrantes — ex. Lopez/Staf SENIOR
                // sans catégorie).
                if (mine.sportType() == null || theirs.sportType() == null
                        || !mine.sportType().equalsIgnoreCase(theirs.sportType())) {
                    throw new AccessDeniedException(
                            "Vous ne pouvez écrire qu'aux collègues de votre discipline");
                }
                // OK — collègue de ma discipline
            } else if (theirs == null) {
                // Cas 2 : destinataire = autre STAFF, ENTRAINEUR, OU PRÉSIDENT de
                // MA discipline (le RosterClient ne lit que player/staff, pas les
                // users PRESIDENT ou ADMIN ; on délègue à AuthUserInfoClient).
                // V2.5 — ajout PRESIDENT : un entraîneur doit pouvoir écrire au
                // président de SA section (canal président dédié), comme un joueur
                // peut déjà le faire (C.21).
                AuthUserInfoClient.UserRoleDiscipline r =
                        authUserInfoClient.getRoleAndDiscipline(recipientUserId);
                boolean sameDiscipline = r != null && r.discipline() != null
                        && r.discipline().equalsIgnoreCase(mine.sportType());
                boolean allowedRole = r != null && (
                        "STAFF".equalsIgnoreCase(r.role())
                        || "ENTRAINEUR".equalsIgnoreCase(r.role())
                        || "PRESIDENT".equalsIgnoreCase(r.role()));
                if (!(sameDiscipline && allowedRole)) {
                    throw new AccessDeniedException(
                            "Vous ne pouvez écrire qu'aux joueurs, collègues ou au président de votre discipline");
                }
            } else {
                // theirs existe avec un rosterRole inattendu (ni JOUEUR ni STAFF) :
                // fail-closed.
                throw new AccessDeniedException(
                        "Vous ne pouvez écrire qu'aux joueurs ou collègues de votre discipline");
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

    /**
     * V2.4 — envoi d'un même message à N destinataires (mode groupe ou
     * individuel selon l'UI entraîneur). Fan-out applicatif : on délègue
     * à {@link #sendToStaffOrPlayer} pour CHAQUE destinataire, ce qui
     * réutilise TOUTE la logique IDOR existante (sameGroup mine/theirs)
     * — pas de duplication, pas de risque d'IDOR caché.
     *
     * <p>Atomicité transactionnelle : si UN destinataire échoue à l'IDOR,
     * l'exception remonte immédiatement et la transaction est rollback —
     * on ne persiste aucun des N messages (pas de demi-envoi).</p>
     */
    @Transactional
    public List<Message> sendBatchToPlayers(List<Long> toUserIds, String content, Attachment attachment) {
        if (toUserIds == null || toUserIds.isEmpty()) {
            throw new IllegalArgumentException("Aucun destinataire");
        }
        if (toUserIds.size() > 50) {
            throw new IllegalArgumentException("Maximum 50 destinataires par envoi");
        }
        // Dédoublonner (l'UI peut envoyer le même id 2 fois)
        List<Long> unique = toUserIds.stream().distinct().toList();
        List<Message> sent = new java.util.ArrayList<>();
        for (Long recipientId : unique) {
            // sendToStaffOrPlayer applique l'IDOR strict sameGroup — pas
            // besoin de re-vérifier ici. Toute violation → AccessDeniedException
            // → rollback transactionnel de tous les messages déjà persistés.
            sent.add(sendToStaffOrPlayer(recipientId, content, attachment));
        }
        return sent;
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

    // ─────────────────────── V2.5 — Mon président ────────────────────────

    /**
     * Lit la fiche roster du connecté (sport+catégorie). Utilisé par
     * {@code GET /my-president} pour résoudre la discipline du président
     * à contacter. Renvoie null si le connecté n'a pas de fiche
     * (ADMIN/PRESIDENT/JOURNALISTE) — dans ce cas « mon président » n'a
     * pas de sens.
     */
    public RosterClient.MembershipInfo findMyRoster(Long userId) {
        if (userId == null) return null;
        return rosterClient.findMembership(userId);
    }

    /**
     * Liste les présidents d'une discipline donnée. Renvoie une liste
     * vide si aucun président n'est enregistré (cas normal pour les
     * disciplines sans gouvernance, ou en cours de rodage).
     */
    public List<AuthUserInfoClient.UserRoleDiscipline> findPresidentsOfDiscipline(String discipline) {
        if (discipline == null || discipline.isBlank()) return List.of();
        return authUserInfoClient.findByRoleAndDiscipline("PRESIDENT", discipline);
    }

    /**
     * C.21 — Vrai si le destinataire est le président de la même discipline
     * que l'appelant. Le RosterClient ne sait lire que les fiches
     * player/staff (pas les users PRESIDENT), donc on délegue à AuthClient.
     * L'appelant doit déjà avoir vérifié {@code mine} (sa propre fiche
     * joueur/staff) pour porter un sportType.
     */
    private boolean isPresidentOfMyDiscipline(RosterClient.MembershipInfo mine, Long recipientUserId) {
        if (mine == null || mine.sportType() == null) return false;
        AuthUserInfoClient.UserRoleDiscipline recipient =
                authUserInfoClient.getRoleAndDiscipline(recipientUserId);
        if (recipient == null || !"PRESIDENT".equalsIgnoreCase(recipient.role())) {
            return false;
        }
        return mine.sportType().equalsIgnoreCase(recipient.discipline());
    }
}
