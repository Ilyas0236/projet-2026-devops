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

    @Lob
    @Column(columnDefinition = "BYTEA")
    private byte[] qrCodeImage;

    private LocalDateTime validatedAt;
    private LocalDateTime cancelledAt;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
