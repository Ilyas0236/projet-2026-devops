package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String phone,
        String firstName,
        String lastName,
        MembershipLevel membershipLevel,
        Role role,
        StatutCompte statutCompte,
        LocalDateTime membershipExpiresAt,
        String referralCode,
        boolean active,
        boolean kycVerified,
        LocalDateTime createdAt
) {}