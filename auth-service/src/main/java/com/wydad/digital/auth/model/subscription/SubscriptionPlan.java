package com.wydad.digital.auth.model.subscription;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Plan d'abonnement saisonnier — éditable par l'ADMIN.
 *
 * Remplace l'ancien enum {@link SubscriptionZoneCode} qui codait en dur
 * la liste des zones commercialisées. L'enum est conservé comme "legacy
 * audit" sur {@code UserSubscription.zoneCode} (rétro-compatibilité PDF
 * et base existante), mais c'est désormais cette entité qui pilote
 * l'offre publique et l'achat.
 *
 * Champs :
 *  - {@code code} : identifiant stable (ex. "PEL-6"), UNIQUE, base du matching
 *    avec l'enum legacy pour les achats pré-migration.
 *  - {@code regularPrice} : prix catalogue pour un supporter standard.
 *  - {@code adherentPrice} : prix réduit pour les supporters ayant déjà
 *    un MembershipLevel payant (Rouge/Or/Diamant) — le front choisit
 *    l'un ou l'autre selon le statut de l'utilisateur connecté.
 *  - {@code isActive} : permet de désactiver un plan sans le supprimer
 *    (les abonnements existants restent valides jusqu'à leur validTo).
 *  - {@code displayOrder} : tri croissant sur la home et la page /abonnement.
 *  - {@code exceptionalPriority} : priorité d'accès aux matchs marqués
 *    EXCEPTIONNEL (cf. fenêtre 48h B.12). Conservé ici pour qu'un plan
 *    "VIP exceptionnel" puisse être flagué.
 */
@Entity
@Table(name = "subscription_plans",
        indexes = {
                @Index(name = "idx_sub_plan_active_order", columnList = "is_active, display_order")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sub_plan_code", columnNames = "code")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code stable (ex. "PEL-6", "VIP-A"). Modifiable par l'admin, mais
     *  la modification casse le lien legacy vers {@link SubscriptionZoneCode}. */
    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** DH — supporter standard (non-adhérent). */
    @Column(name = "regular_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal regularPrice;

    /** DH — supporter déjà adhérent (réduction héritée de MembershipLevel). */
    @Column(name = "adherent_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal adherentPrice;

    /** Avantages affichés sur la home et la page /abonnement. */
    @Column(name = "benefits", columnDefinition = "TEXT")
    private String benefits;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(name = "exceptional_priority", nullable = false)
    private Boolean exceptionalPriority = false;

    /** Saison sportive de référence (ex. "2026-2027"). Informationnel ;
     *  la date de fin effective reste portée par {@code UserSubscription.validTo}. */
    @Column(name = "season", length = 16)
    private String season;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
