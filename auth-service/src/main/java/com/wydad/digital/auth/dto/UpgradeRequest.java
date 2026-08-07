package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record UpgradeRequest(
        @Email String email,
        @NotNull MembershipLevel newLevel
) {}