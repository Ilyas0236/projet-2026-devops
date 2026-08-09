package com.wydad.digital.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DonRequest(
        @NotNull @Email String email,
        @NotNull @Min(10) BigDecimal amount,
        @NotBlank String type, // "PONCTUEL" ou "MENSUEL"
        String message,
        boolean recuFiscal
) {}