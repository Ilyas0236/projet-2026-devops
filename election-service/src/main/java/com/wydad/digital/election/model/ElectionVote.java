package com.wydad.digital.election.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Vote d'un adhérent pour UN candidat d'une élection. Contrainte SQL
 * (election_id, user_id) : un membre vote UNE fois — dernier rempart contre
 * le double vote, même en concurrence.
 */
@Entity
@Table(name = "election_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"election_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ElectionVote {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Vient du contexte gateway (X-User-Id), jamais du body. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Candidat choisi. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private ElectionCandidate candidate;

    @CreationTimestamp
    private LocalDateTime votedAt;
}
