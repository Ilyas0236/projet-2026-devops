package com.wydad.digital.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CardPaymentRequest(
        @NotBlank @Pattern(regexp = "\\d{16}") String cardNumber,
        @NotBlank @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}") String expiryDate,
        @NotBlank @Size(min = 3, max = 3) String cvv,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp,
        @NotNull BigDecimal amount
) {}