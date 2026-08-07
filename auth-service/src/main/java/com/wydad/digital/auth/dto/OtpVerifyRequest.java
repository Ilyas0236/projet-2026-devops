package com.wydad.digital.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 6) String code
) {}