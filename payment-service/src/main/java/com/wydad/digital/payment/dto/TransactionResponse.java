package com.wydad.digital.payment.dto;

import com.wydad.digital.payment.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String email,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String reference,
        LocalDateTime createdAt
) {}