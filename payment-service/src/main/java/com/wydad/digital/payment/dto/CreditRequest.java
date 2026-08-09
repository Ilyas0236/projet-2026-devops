package com.wydad.digital.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreditRequest(
        @NotNull @Email String email,
        @NotNull @Min(1) BigDecimal amount,
        String description
) {}