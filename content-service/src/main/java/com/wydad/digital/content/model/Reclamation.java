package com.wydad.digital.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * B.10 — Réclamation d'un membre (boutique, billetterie, adhésion…).
 * L'identité du plaignant est TOUJOURS imposée par le serveur depuis les
 * en-têtes gateway (jamais lue du corps de requête). La réponse est
 * exclusivement le fait de l'ADMIN.
 */
@Entity
@Table(name = "reclamations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reclamation {

    public enum Subject { SHOP, TICKETING, MEMBERSHIP, WEBSITE, OTHER }

    public enum Status { OPEN, IN_PROGRESS, RESOLVED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 180)
    private String userEmail;

    /** Sujet de la réclamation. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Subject subject;

    @Column(nullable = false, length = 150)
    private String title;

    /** Description détaillée fournie par le membre. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    /**
     * Réponse officielle du club — saisie par l'ADMIN uniquement ;
     * null tant que la réclamation n'a pas été traitée.
     */
    @Column(length = 2000)
    private String adminResponse;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
