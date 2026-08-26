package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.TicketCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PurchaseTicketRequest {
    @NotNull private Long eventId;
    @NotNull private Long userId;
    private String userFullName;
    private String userEmail;
    @NotNull private TicketCategory category;
    @Positive private Integer quantity;
    private String paymentMethod = "ECASH";
}
