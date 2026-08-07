package com.wydad.digital.auth.dto;

import com.wydad.digital.auth.model.MembershipLevel;

import java.time.LocalDateTime;

public record MembershipStatusResponse(
        String email,
        MembershipLevel currentLevel,
        LocalDateTime expiresAt,
        String status,      // ACTIF, J-30, J-7, J-1, EXPIRE
        String message,
        int daysRemaining
) {}