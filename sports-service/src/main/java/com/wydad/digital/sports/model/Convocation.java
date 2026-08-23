package com.wydad.digital.sports.model;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Convocation d'un joueur par le staff (B.3.a) : associe un joueur à une
 * séance. Le joueur confirme sa présence depuis son espace privé.
 * L'identité du joueur est stockée via son userId auth-service.
 */
@Entity
@Table(name = "convocations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Convocation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long joueurUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private Session session;

    /** Catégorie/sport de la séance, dénormalisés pour l'affichage liste. */
    @Enumerated(EnumType.STRING) private SportType sportType;
    @Enumerated(EnumType.STRING) private Category category;

    /**
     * Réponse du joueur : null = en attente, CONFIRME / ABSENT / RETARD.
     * La justification n'est renseignée que pour ABSENT/RETARD.
     */
    public enum ResponseStatus { CONFIRME, ABSENT, RETARD }

    @Enumerated(EnumType.STRING)
    private ResponseStatus responseStatus;

    @Column(length = 500)
    private String responseJustification;

    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private Long createdByStaffId;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
