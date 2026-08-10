package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.SeatType;
import com.wydad.digital.ticket.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SectionRequest {
    @NotBlank private String name;
    @NotNull private TicketCategory category;
    private SeatType seatType;
    @NotNull @Positive private Integer capacity;
    @NotNull @Positive private BigDecimal price;
}
