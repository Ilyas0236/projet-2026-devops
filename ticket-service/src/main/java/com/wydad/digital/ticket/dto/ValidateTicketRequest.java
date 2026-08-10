package com.wydad.digital.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ValidateTicketRequest {
    @NotBlank private String qrCodeData;
}
