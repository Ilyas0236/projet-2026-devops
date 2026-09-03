package com.wydad.digital.auth.repository.press;

import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.press.PressAccreditation;
import com.wydad.digital.auth.model.press.PressAccreditationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Historique complet (traçabilité admin) : toutes les demandes, triées
     * par date de décision DESC. Les EN_ATTENTE (sans decidedAt) remontent
     * via COALESCE → createdAt, donc elles apparaissent en tête, suivies
     * des VALIDE/REFUSE les plus récentes. @Query obligatoire car Spring
     * Data ne gère pas nativement un COALESCE dans un ORDER BY.
     */
    @Query("SELECT p FROM PressAccreditation p " +
           "ORDER BY COALESCE(p.decidedAt, p.createdAt) DESC")
    List<PressAccreditation> findAllOrderByDecisionDateDesc();
}
