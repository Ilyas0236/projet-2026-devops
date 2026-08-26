package com.wydad.digital.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipLevel membershipLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Phase 0 : circuit de validation. Les comptes ADHERENT sont VALIDE
     * dès l'inscription ; les rôles privilégiés (ENTRAINEUR, JOURNALISTE,
     * PRESIDENT) passent par la validation d'un ADMIN avant de pouvoir
     * se connecter.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCompte statutCompte = StatutCompte.VALIDE;

    /** Motif du refus (visible dans l'écran admin des demandes). */
    private String motifRefus;

    private LocalDateTime membershipExpiresAt;

    private String referralCode;

    private String referredBy;

    private String ville;
    private String langue = "fr";
    private String timezone = "Africa/Casablanca";
    private String bio;

    /** Discipline sportive sollicitée (FOOTBALL/BASKETBALL/HANDBALL/AUTRE)
     * pour JOUEUR, ENTRAINEUR ou STAFF — renseignée à l'inscription,
     * confirmée par l'admin. Toujours accompagnée de categorieDemandee :
     * un compte sportif est rattaché au couple discipline+catégorie. */
    private String disciplineDemandee;

    /** Catégorie sportive sollicitée (U15/U17/U18/U20/SENIOR) pour JOUEUR,
     * ENTRAINEUR ou STAFF — renseignée à l'inscription, confirmée par l'admin. */
    private String categorieDemandee;

    /** Accréditation presse (JOURNALISTE) : organe/site de travail déclaré. */
    private String organismePresse;

    /**
     * §17 — accréditation presse liée à un match RÉEL du calendrier
     * content-service (id vérifié à l'inscription par appel interne).
     * matchCouvreLabel en est le libellé figé affiché sur le badge.
     */
    private Long matchId;

    /** Libellé figé du match couvert (ex. « Wydad vs Raja — Botola Pro »). */
    private String matchSouhaite;

    private boolean kycVerified = false;

    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}