package com.wydad.digital.auth.model.press;

import com.wydad.digital.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * B.17 — Demande d'accréditation presse (multi-matchs).
 *
 * <p>Une ligne = une demande d'un journaliste pour couvrir UN match du
 * calendrier. Un même journaliste peut créer N demandes (N matchs), mais
 * une seule par match — garanti par la contrainte UNIQUE
 * {@code (user_id, match_id)}.</p>
 *
 * <p>Cycle de vie : {@link PressAccreditationStatus#EN_ATTENTE} créé par le
 * journaliste → décision admin ({@code VALIDE} ou {@code REFUSE}) avec
 * notif in-app au demandeur à chaque transition.</p>
 *
 * <p>Champs dénormalisés (matchLabel, matchDate, organismePresse) : figés à
 * la création de la demande. Si l'admin renomme un match plus tard, le
 * badge du journaliste reste exact (le libellé est mémorisé au moment de
 * la décision).</p>
 */
@Entity
@Table(name = "press_accreditations",
        uniqueConstraints = @UniqueConstraint(name = "uk_press_user_match",
                columnNames = {"user_id", "match_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PressAccreditation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** id du match dans content-service (clé externe, pas de FK — découplage microservice). */
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    /** Libellé du match figé à la création (ex. "Wydad vs Raja — Botola Pro, le 2026-09-12"). */
    @Column(name = "match_label", nullable = false, length = 256)
    private String matchLabel;

    /** Date du match dénormalisée pour pouvoir trier sans appel à content-service. */
    @Column(name = "match_date")
    private LocalDate matchDate;

    /** Média du journaliste figé à la création (organismePresse du User au moment de la demande). */
    @Column(name = "organisme_presse", nullable = false, length = 128)
    private String organismePresse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PressAccreditationStatus statut;

    /** Motif écrit par l'admin en cas de refus — affiché au journaliste dans son espace. */
    @Column(name = "motif_refus", length = 512)
    private String motifRefus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Date de la décision admin (validate / refuse). */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Admin qui a tranché (FK vers users.id, nullable tant que EN_ATTENTE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;
}
