package com.wydad.digital.election.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Candidat à une élection présidentielle : nom, photo (URL Cloudinary
 * publique, affichable sans authentification) et courte présentation.
 *
 * <p>B.8 — Le champ {@code userId} (nullable) permet de lier un candidat
 * à un titulaire de carte d'abonnement actif (consommé par
 * {@code election-service} pour la validation d'éligibilité). La colonne
 * est gérée par Hibernate {@code ddl-auto: update} (pas de Flyway sur
 * ce service, par décision projet).</p>
 *
 * <p>Le champ reste <strong>nullable</strong> pour deux raisons :</p>
 * <ul>
 *   <li>rétro-compat : les candidats historiques (texte libre) restent
 *       valides, leurs votes sont préservés ;</li>
 *   <li>cas dégradé : un admin peut ajouter un candidat « externe »
 *       avant que la liaison titulaire ne soit saisie (à compléter
 *       avant clôture).</li>
 * </ul>
 *
 * <p>Un candidat SANS userId ne peut PAS recevoir de vote (la règle
 * « seul un titulaire peut voter ET être candidat » est appliquée
 * côté service à la clôture).</p>
 */
@Entity
@Table(name = "election_candidates",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_election_candidate_user",
               columnNames = {"election_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ElectionCandidate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false)
    private String fullName;

    /** Courte présentation du candidat (max 1000 caractères). */
    @Column(length = 1000)
    private String presentation;

    /** Photo publique (Cloudinary type=upload) — visible sur le site officiel. */
    private String photoUrl;

    /** Ordre d'affichage dans les listes. */
    @Builder.Default
    private int displayOrder = 0;

    /**
     * B.8 — FK logique vers le titulaire actif (auth-service). Nullable
     * pour rétro-compat (cf. javadoc classe). La contrainte d'unicité
     * (election_id, user_id) empêche d'inscrire deux fois le même
     * titulaire dans la même élection.
     *
     * <p>Pas de {@code @ManyToOne} : on ne navigue jamais vers User
     * côté election-service, on consomme la liste titulaires via le
     * client REST ActiveMembersClient (cf. commit 4).</p>
     */
    @Column(name = "user_id")
    private Long userId;
}

