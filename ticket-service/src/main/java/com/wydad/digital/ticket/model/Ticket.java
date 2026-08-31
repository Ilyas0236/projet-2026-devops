package com.wydad.digital.ticket.model;

import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticketNumber;

    @Column(nullable = false)
    private Long userId;

    private String userFullName;
    private String userEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TicketStatus status = TicketStatus.RESERVED;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private String seatNumber;

    @Column(unique = true)
    private String qrCodeData;

    // Pas de @Lob : sous Hibernate 6 + PostgreSQL, @Lob mappe byte[] vers le
    // type OID (large object) alors que la colonne est BYTEA -> echec d'insertion.
    // Un byte[] sans @Lob mappe nativement vers BYTEA.
    @Column(columnDefinition = "BYTEA")
    private byte[] qrCodeImage;

    /**
     * B.18 — Achat PARENT pour un enfant académie. Si non NULL, ce billet
     * a été acheté par un parent au nom de son enfant (User shadow).
     * NULL = achat « pour soi » (cas ADHERENT, ADMIN, PARENT pour lui-même).
     * La colonne permet les listings admin et la traçabilité.
     */
    @Column(name = "beneficiary_academy_member_id")
    private Long beneficiaryAcademyMemberId;

    /**
     * B.18 — Email du parent payeur pour les billets offerts à un enfant.
     * NULL pour les achats « pour soi ». Permet le remboursement E-Cash
     * (le wallet débité est celui du parent) et l'affichage back-office.
     */
    @Column(name = "parent_payer_email", length = 256)
    private String parentPayerEmail;

    private LocalDateTime validatedAt;
    private LocalDateTime cancelledAt;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
