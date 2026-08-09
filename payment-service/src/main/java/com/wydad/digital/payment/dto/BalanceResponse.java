package com.wydad.digital.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceResponse(
        String email,
        BigDecimal balance,
        LocalDateTime updatedAt
) {}