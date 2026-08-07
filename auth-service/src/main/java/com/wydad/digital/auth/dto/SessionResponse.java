package com.wydad.digital.auth.dto;

import java.time.LocalDateTime;

public record SessionResponse(
        Long id,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        boolean currentSession
) {}