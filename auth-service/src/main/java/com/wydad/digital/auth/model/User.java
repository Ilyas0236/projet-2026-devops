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

    private boolean kycVerified = false;

    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}