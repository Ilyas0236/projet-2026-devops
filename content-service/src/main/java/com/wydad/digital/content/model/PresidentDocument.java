package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * B.15 — Document interne soumis par le PRÉSIDENT et traité par l'ADMIN.
 * Workflow : DRAFT (président) → SUBMITTED (président) → APPROVED (admin)
 *            → PUBLISHED (admin) ou REJECTED (admin, avec motif).
 *
 * L'identité du président est TOUJOURS imposée par la gateway (X-User-Id,
 * X-User-Email) — jamais lue du corps de requête. L'admin est tracé
 * séparément (adminUserId) pour audit.
 */
@Entity
@Table(name = "president_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PresidentDocument {

    /** Cycle de vie du document (B.15). */
    public enum Status {
        /** Brouillon : seul le PRÉSIDENT auteur peut le modifier. */
        DRAFT,
        /** Soumis à l'ADMIN pour traitement. Plus modifiable par le PRÉSIDENT. */
        SUBMITTED,
        /** Validé par l'ADMIN mais pas encore publié. */
        APPROVED,
        /** Rendu visible aux membres (publié). */
        PUBLISHED,
        /** Refusé par l'ADMIN (motif obligatoire). */
        REJECTED
    }

    public enum Category {
        RAPPORT_MORAL,      // Rapport moral de saison
        RAPPORT_FINANCIER,  // États financiers annuels
        COMMUNIQUE,         // Communiqué officiel du Président
        PROJET_CLUB,        // Vision / projet stratégique
        COMPTE_RENDU_CA     // Compte rendu du conseil d'administration
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long presidentUserId;

    @Column(nullable = false, length = 180)
    private String presidentEmail;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** PDF généré côté serveur (B.22) — stocké en BDD ou chemin Cloudinary. */
    @Column(length = 500)
    private String pdfUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    /** Admin qui a traité (validé / publié / refusé) — null tant que pas traité. */
    private Long adminUserId;

    @Column(length = 180)
    private String adminEmail;

    /** Motif de refus (obligatoire si status=REJECTED). */
    @Column(length = 1000)
    private String motifRejet;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Date de publication effective (status=PUBLISHED). */
    private LocalDateTime publishedAt;
}
