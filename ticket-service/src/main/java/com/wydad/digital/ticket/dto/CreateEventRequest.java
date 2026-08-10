package com.wydad.digital.ticket.dto;

import com.wydad.digital.ticket.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateEventRequest {
    @NotBlank private String title;
    private String description;
    @NotNull private EventType eventType;
    @NotBlank private String homeTeam;
    private String awayTeam;
    @NotBlank private String venue;
    private String competition;
    @NotNull private LocalDateTime eventDate;
    private LocalDateTime gateOpenTime;
    @NotNull @Positive private BigDecimal basePrice;
    @NotNull @Positive private Integer totalCapacity;
    private String posterUrl;
    private List<SectionRequest> sections;
}
