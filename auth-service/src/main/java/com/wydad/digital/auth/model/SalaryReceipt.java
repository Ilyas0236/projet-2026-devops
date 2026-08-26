package com.wydad.digital.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Phase 5 bis / Espace Président — reçu PDF de paiement (salaire ou prime)
 * émis par le PRÉSIDENT à destination d'un joueur ou d'un agent du club.
 *
 * <p>Le PDF est généré à la volée (OpenPDF) — seules les métadonnées sont
 * persistées. Visibilité stricte : l'agent ne voit que SES reçus, le
 * président et l'admin voient tout (contrôle serveur, jamais côté client).</p>
 */
@Entity
@Table(name = "salary_receipts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bénéficiaire (joueur ou staff administratif). */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String userFullName;

    @Column(nullable = false)
    private String userEmail;

    /** SALAIRE | PRIME. */
    @Column(nullable = false, length = 20)
    private String receiptType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    /** Période couverte (ex. « Août 2026 ») pour un salaire ; null pour une prime. */
    @Column(length = 50)
    private String periode;

    /** Motif libre pour une prime (ex. « Prime de championnat »). */
    @Column(length = 200)
    private String motif;

    /** Référence unique imprimée sur le PDF (WAC-REC-2026-000001). */
    @Column(unique = true, nullable = false)
    private String reference;

    private LocalDate paymentDate;

    /** Auteur de l'émission (compte PRESIDENT). */
    @Column(nullable = false)
    private Long issuedByUserId;

    @Column(nullable = false, length = 100)
    private String issuedByName;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
