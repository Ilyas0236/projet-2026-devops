package com.wydad.digital.election.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Candidat à une élection présidentielle : nom, photo (URL Cloudinary
 * publique, affichable sans authentification) et courte présentation.
 */
@Entity
@Table(name = "election_candidates")
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
}
