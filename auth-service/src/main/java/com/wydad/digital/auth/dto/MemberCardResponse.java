package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;

public record MemberCardResponse(
        String email,
        String firstName,
        String lastName,
        MembershipLevel membershipLevel,
        String referralCode,
        String qrCodeBase64
) {}