package com.wydad.digital.election.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Élection du président du club — domaine GOUVERNANCE, service dédié.
 *
 * <p>Cycle de vie B.8 (transitions d'état ISTQB) :</p>
 * <pre>
 *   OPEN ---(clôture ADMIN ou scheduler)--&gt; CLOSED(published=false)
 *                                              |
 *                                              +--(publish ADMIN, si
 *                                              |   votes == éligibles)
 *                                              v
 *                                          CLOSED(published=true)
 * </pre>
 *
 * <p>B.8 — La clôture et la publication sont désormais deux étapes
 * distinctes :
 * <ul>
 *   <li>{@code closeOnly} : gèle le scrutin (status=CLOSED,
 *       published=false, ferme closedAt). Aucun résultat n'est encore
 *       public — l'admin peut encore auditer les votes ;</li>
 *   <li>{@code publishResults} : exige que <b>tous les titulaires
 *       éligibles aient voté</b> (count voteRepository ==
 *       eligibleVotersCount figé à endsAt). Calcule le gagnant,
 *       set published=true, notifie. Idempotent (replay=200 no-op).</li>
 * </ul>
 * </p>
 *
 * <p>Le mode « close + publish immédiat » reste disponible via
 * {@code closeAndPublish} (alias pour rétro-compat interne, exposé
 * au front admin en bouton "Tout-en-un").</p>
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

    /**
     * B.8 — Date de clôture (gèle, SANS publier). Null tant que
     * l'élection est OPEN. Renseigné par {@code closeOnly}. Permet
     * à l'admin d'auditer la fenêtre de vote close (vote clos
     * depuis X) sans pour autant publier les résultats.
     */
    private LocalDateTime closedAt;
}
