package com.wydad.digital.auth.model.subscription;

import com.wydad.digital.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Abonnement saisonnier d'un supporter.
 *
 * Cycle de vie :
 *   1. Paiement validé par payment-service (transactionId rempli)
 *   2. Création en base (status = ACTIVE, paidAt = maintenant)
 *   3. Génération du QR code (qrCode) + chemin du PDF (pdfPath) en // par PdfService
 *   4. À expiration de la saison : status = EXPIRED, plus aucun accès stade
 *
 * Un supporter ne peut avoir qu'UN abonnement ACTIF à la fois (vérifié en
 * service). Le prix figé (paidAmount) est conservé pour la facture.
 */
@Entity
@Table(name = "user_subscriptions",
        indexes = {
                @Index(name = "idx_user_subscription_active", columnList = "user_id, status"),
                @Index(name = "idx_user_subscription_zone", columnList = "zone_code")
        },
        uniqueConstraints = {
                // Empêche deux abonnements ACTIFS simultanés sur le même user.
                // MySQL ne supporte pas les index partiels nativement, donc on
                // s'appuie sur l'unicité (user_id, season) + check applicatif.
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_code", nullable = false, length = 16)
    private SubscriptionZoneCode zoneCode;

    /**
     * Plan d'abonnement FK (B.12 dynamique). Nullable pour la cohabitation
     * avec les lignes pré-migration : un backfill SQL (cf. A.11 du plan)
     * remplit {@code plan_id} à partir de {@code zone_code} lors du passage
     * en prod. ON DELETE SET NULL côté FK — supprimer un plan ne supprime
     * pas l'historique des abonnements.
     *
     * <p>À terme, c'est ce champ qui pilote l'offre ; {@link #zoneCode}
     * est conservé comme "legacy audit" (PDF + table user_subscriptions
     * déjà en prod) et n'est plus alimenté pour les nouveaux achats que
     * par dérivation du code du plan.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private SubscriptionPlan plan;

    /** Saison sportive (ex. "2026-2027"). Calculée à l'achat. */
    @Column(name = "season", nullable = false, length = 16)
    private String season;

    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount;

    /** Référence de la transaction payment-service. */
    @Column(name = "transaction_ref", length = 64)
    private String transactionRef;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDateTime validTo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 16)
    private UserSubscriptionStatus status = UserSubscriptionStatus.PENDING_PAYMENT;

    /** QR code (base64 PNG) — généré après confirmation paiement. */
    @Lob
    @Column(name = "qr_code_base64", columnDefinition = "TEXT")
    private String qrCodeBase64;

    /** Chemin du PDF généré (Cloudinary publicId ou stockage local). */
    @Column(name = "pdf_path", length = 512)
    private String pdfPath;
}
