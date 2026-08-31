package com.wydad.digital.auth.repository.press;

import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.press.PressAccreditation;
import com.wydad.digital.auth.model.press.PressAccreditationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PressAccreditationRepository extends JpaRepository<PressAccreditation, Long> {

    /** Ses demandes, triées de la plus récente à la plus ancienne (vue "mes demandes"). */
    List<PressAccreditation> findByUserOrderByCreatedAtDesc(User user);

    /** Demandes du journaliste filtrées par statut (utile pour le bouton "en attente"/"refusée"). */
    List<PressAccreditation> findByUserAndStatutOrderByCreatedAtDesc(User user, PressAccreditationStatus statut);

    /** Toutes les demandes EN_ATTENTE triées FIFO (les plus anciennes en premier pour l'admin). */
    List<PressAccreditation> findByStatutOrderByCreatedAtAsc(PressAccreditationStatus statut);

    /** Anti-doublon : un journaliste ne peut pas créer 2 demandes pour le même match. */
    boolean existsByUserAndMatchId(User user, Long matchId);
}
