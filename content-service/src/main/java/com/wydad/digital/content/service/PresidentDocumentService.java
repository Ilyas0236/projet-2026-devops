package com.wydad.digital.content.service;

import com.wydad.digital.content.model.PresidentDocument;
import com.wydad.digital.content.repository.PresidentDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PresidentDocumentService {

    private final PresidentDocumentRepository repository;

    // ==================== PRÉSIDENT ====================

    /** Crée un document en DRAFT (brouillon modifiable). */
    @Transactional
    public PresidentDocument createDraft(Long presidentUserId, String presidentEmail,
                                          PresidentDocument.Category category,
                                          String title, String content) {
        PresidentDocument doc = PresidentDocument.builder()
                .presidentUserId(presidentUserId)
                .presidentEmail(presidentEmail)
                .category(category)
                .title(title)
                .content(content)
                .status(PresidentDocument.Status.DRAFT)
                .build();
        return repository.save(doc);
    }

    /** Modifie un DRAFT — le PRÉSIDENT ne peut modifier que SES documents. */
    @Transactional
    public PresidentDocument updateDraft(Long docId, Long presidentUserId, String title, String content) {
        PresidentDocument doc = getOrThrow(docId);
        if (!doc.getPresidentUserId().equals(presidentUserId)) {
            throw new SecurityException("Vous n'êtes pas l'auteur de ce document");
        }
        if (doc.getStatus() != PresidentDocument.Status.DRAFT) {
            throw new IllegalStateException("Seul un brouillon peut être modifié (statut actuel : " + doc.getStatus() + ")");
        }
        doc.setTitle(title);
        doc.setContent(content);
        return repository.save(doc);
    }

    /** Soumet un DRAFT à l'ADMIN — verrouillé ensuite. */
    @Transactional
    public PresidentDocument submit(Long docId, Long presidentUserId) {
        PresidentDocument doc = getOrThrow(docId);
        if (!doc.getPresidentUserId().equals(presidentUserId)) {
            throw new SecurityException("Vous n'êtes pas l'auteur de ce document");
        }
        if (doc.getStatus() != PresidentDocument.Status.DRAFT) {
            throw new IllegalStateException("Seul un brouillon peut être soumis (statut actuel : " + doc.getStatus() + ")");
        }
        doc.setStatus(PresidentDocument.Status.SUBMITTED);
        return repository.save(doc);
    }

    /** Liste les documents du PRÉSIDENT connecté. */
    public List<PresidentDocument> mine(Long presidentUserId) {
        return repository.findByPresidentUserIdOrderByCreatedAtDesc(presidentUserId);
    }

    // ==================== ADMIN ====================

    /** File d'attente admin. */
    public List<PresidentDocument> pendingForAdmin() {
        return repository.findByStatusOrderByCreatedAtAsc(PresidentDocument.Status.SUBMITTED);
    }

    /** Valide un document soumis (admin only). */
    @Transactional
    public PresidentDocument approve(Long docId, Long adminUserId, String adminEmail) {
        PresidentDocument doc = getOrThrow(docId);
        if (doc.getStatus() != PresidentDocument.Status.SUBMITTED) {
            throw new IllegalStateException("Seul un document soumis peut être validé (statut actuel : " + doc.getStatus() + ")");
        }
        doc.setStatus(PresidentDocument.Status.APPROVED);
        doc.setAdminUserId(adminUserId);
        doc.setAdminEmail(adminEmail);
        return repository.save(doc);
    }

    /** Publie un document déjà validé (admin only) — visible des membres. */
    @Transactional
    public PresidentDocument publish(Long docId, Long adminUserId, String adminEmail) {
        PresidentDocument doc = getOrThrow(docId);
        if (doc.getStatus() != PresidentDocument.Status.APPROVED) {
            throw new IllegalStateException("Seul un document validé peut être publié (statut actuel : " + doc.getStatus() + ")");
        }
        doc.setStatus(PresidentDocument.Status.PUBLISHED);
        doc.setPublishedAt(LocalDateTime.now());
        // idempotent : on garde l'admin traceur
        if (doc.getAdminUserId() == null) {
            doc.setAdminUserId(adminUserId);
            doc.setAdminEmail(adminEmail);
        }
        return repository.save(doc);
    }

    /** Refuse un document soumis avec motif obligatoire. */
    @Transactional
    public PresidentDocument reject(Long docId, Long adminUserId, String adminEmail, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif de refus est obligatoire");
        }
        PresidentDocument doc = getOrThrow(docId);
        if (doc.getStatus() != PresidentDocument.Status.SUBMITTED) {
            throw new IllegalStateException("Seul un document soumis peut être refusé (statut actuel : " + doc.getStatus() + ")");
        }
        doc.setStatus(PresidentDocument.Status.REJECTED);
        doc.setAdminUserId(adminUserId);
        doc.setAdminEmail(adminEmail);
        doc.setMotifRejet(motif);
        return repository.save(doc);
    }

    // ==================== MEMBRES (lecture publique) ====================

    /** Documents publiés — visibles des membres authentifiés. */
    public List<PresidentDocument> published() {
        return repository.findByStatusOrderByPublishedAtDesc(PresidentDocument.Status.PUBLISHED);
    }

    /** Lecture d'un document publié (membres) ou de ses propres documents (président). */
    public PresidentDocument getForRead(Long docId, Long currentUserId, boolean isAdmin) {
        PresidentDocument doc = getOrThrow(docId);
        if (isAdmin) {
            return doc;
        }
        // Président : peut voir les siens à tout statut
        if (doc.getPresidentUserId().equals(currentUserId)) {
            return doc;
        }
        // Membres : seulement PUBLISHED
        if (doc.getStatus() != PresidentDocument.Status.PUBLISHED) {
            throw new EntityNotFoundException("Document non publié");
        }
        return doc;
    }

    // ==================== HELPERS ====================

    private PresidentDocument getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document président non trouvé : " + id));
    }
}
