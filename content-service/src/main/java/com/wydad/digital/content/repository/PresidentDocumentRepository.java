package com.wydad.digital.content.repository;

import com.wydad.digital.content.model.PresidentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PresidentDocumentRepository extends JpaRepository<PresidentDocument, Long> {

    /** Tous les documents d'un président, plus récent d'abord. */
    List<PresidentDocument> findByPresidentUserIdOrderByCreatedAtDesc(Long presidentUserId);

    /** File d'attente admin : tous les SUBMITTED + APPROVED (non encore publiés). */
    List<PresidentDocument> findByStatusInOrderByCreatedAtAsc(List<PresidentDocument.Status> statuses);

    /** Documents publiés, consultables par les membres. */
    List<PresidentDocument> findByStatusOrderByPublishedAtDesc(PresidentDocument.Status status);

    /** Soumissions en attente uniquement. */
    List<PresidentDocument> findByStatusOrderByCreatedAtAsc(PresidentDocument.Status status);
}
