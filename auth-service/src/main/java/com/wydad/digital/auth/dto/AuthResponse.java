package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String email,
        String firstName,
        String lastName,
        MembershipLevel membershipLevel,
        String referralCode,
        String role
) {}