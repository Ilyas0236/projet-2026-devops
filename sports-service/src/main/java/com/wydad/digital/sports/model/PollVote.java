package com.wydad.digital.sports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * B.2 — Vote d'un membre sur un sondage. La contrainte d'unicité
 * (poll_id, user_id) garantit au niveau BASE DE DONNEES qu'un utilisateur
 * ne peut voter qu'une fois par sondage — pas seulement côté code.
 */
@Entity
@Table(name = "poll_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"poll_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PollVote {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    /** Vient du contexte JWT (X-User-Id), jamais du body de la requête. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Index de l'option choisie dans Poll.options. */
    @Column(nullable = false)
    private int optionIndex;

    @CreationTimestamp
    private LocalDateTime votedAt;
}
