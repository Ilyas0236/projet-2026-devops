package com.wydad.digital.election.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Élection du président du club — domaine GOUVERNANCE, service dédié.
 *
 * Cycle de vie (transitions d'état ISTQB) :
 *   OPEN -> CLOSED (à la date de fin, par le scheduler, ou manuellement ADMIN)
 *
 * À la clôture les résultats sont calculés puis PUBLIÉS automatiquement :
 * le gagnant apparaît dans l'espace adhérent ET sur le site public
 * (visible sans connexion).
 */
@Entity
@Table(name = "elections")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Election {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    /** Date de début du vote (inclusive). */
    @Column(nullable = false)
    private LocalDateTime startsAt;

    /** Date de fin du vote ; à son passage le scheduler clôture et publie. */
    @Column(nullable = false)
    private LocalDateTime endsAt;

    /**
     * OPEN : votes acceptés. CLOSED : résultats figés et publiés.
     * La transition est à sens unique — jamais de réouverture.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ElectionStatus status = ElectionStatus.OPEN;

    /**
     * Résultats publiés ? Vrai dès la clôture (auto ou manuelle). Séparé de
     * status pour permettre plus tard un mode « dépouillement différé » sans
     * toucher au contrat des clients.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    /** Id du candidat gagnant, figé à la publication (null si aucun vote). */
    private Long winnerCandidateId;

    private String createdByEmail;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
