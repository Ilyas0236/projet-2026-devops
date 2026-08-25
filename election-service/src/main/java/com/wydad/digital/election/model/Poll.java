package com.wydad.digital.election.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * B.2 — Sondage : second pilier du domaine GOUVERNANCE/PARTICIPATION
 * (avec l'élection du président). Mêmes règles de vote unique qu'une
 * élection — un seul moteur conceptuel « question -> choix -> vote ».
 * Les options sont stockées dans l'ordre (option_index) ; les résultats
 * sont TOUJOURS calculés côté serveur à partir des votes enregistrés.
 */
@Entity
@Table(name = "polls")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Poll {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String question;

    @ElementCollection
    @CollectionTable(name = "poll_options", joinColumns = @JoinColumn(name = "poll_id"))
    @OrderColumn(name = "option_index")
    @Column(nullable = false)
    @Builder.Default
    private List<String> options = new ArrayList<>();

    /** Un sondage désactivé n'accepte plus de votes et disparaît des listes actives. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Clôture optionnelle automatique. */
    private LocalDateTime closesAt;

    private String createdByEmail;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
