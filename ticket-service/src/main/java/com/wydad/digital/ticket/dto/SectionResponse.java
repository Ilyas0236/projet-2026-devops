package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.SeatType;
import com.wydad.digital.ticket.enums.TicketCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class SectionResponse {
    private Long id;
    private String name;
    private TicketCategory category;
    private SeatType seatType;
    private Integer capacity;
    private Integer availableSeats;
    private BigDecimal price;
}
