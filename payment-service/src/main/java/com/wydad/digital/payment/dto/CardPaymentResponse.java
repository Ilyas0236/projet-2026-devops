package com.wydad.digital.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardPaymentResponse {
    private boolean success;
    private String message;
    private String transactionId;
    private BigDecimal amount;
    private LocalDateTime timestamp;
}