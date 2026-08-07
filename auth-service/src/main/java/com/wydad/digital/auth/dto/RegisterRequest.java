package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull MembershipLevel membershipLevel,
        String referralCode
) {}