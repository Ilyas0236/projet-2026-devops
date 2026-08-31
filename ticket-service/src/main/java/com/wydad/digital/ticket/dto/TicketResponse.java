package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.TicketCategory;
import com.wydad.digital.ticket.enums.TicketStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class TicketResponse {
    private Long id;
    private String ticketNumber;
    private Long userId;
    private String userFullName;
    private Long eventId;
    private String eventTitle;
    private LocalDateTime eventDate;
    private String venue;
    private TicketCategory category;
    private String sectionName;
    private String seatNumber;
    private TicketStatus status;
    private BigDecimal price;
    private String qrCodeData;
    private LocalDateTime createdAt;
    /** B.18 — id AcademyMember si billet acheté par un parent pour son enfant. */
    private Long beneficiaryAcademyMemberId;
    /** B.18 — email du parent payeur (billets enfants). */
    private String parentPayerEmail;
}
