package com.wydad.digital.auth.dto;

import java.time.LocalDateTime;

public record KycResponse(
        String email,
        String documentType,
        String documentNumber,
        boolean verified,
        LocalDateTime uploadedAt
) {}