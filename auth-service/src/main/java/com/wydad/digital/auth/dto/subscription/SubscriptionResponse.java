package com.wydad.digital.auth.dto.subscription;

import com.wydad.digital.auth.model.subscription.SubscriptionZoneCode;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.model.subscription.UserSubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long id,
        String email,
        String zoneCode,
        String zoneDisplayName,
        String season,
        BigDecimal paidAmount,
        String transactionRef,
        LocalDateTime paidAt,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        UserSubscriptionStatus status,
        String qrCodeBase64,
        String pdfPath
) {
    public static SubscriptionResponse from(UserSubscription s) {
        return new SubscriptionResponse(
                s.getId(),
                s.getUser().getEmail(),
                s.getZoneCode().getCode(),
                s.getZoneCode().getDisplayName(),
                s.getSeason(),
                s.getPaidAmount(),
                s.getTransactionRef(),
                s.getPaidAt(),
                s.getValidFrom(),
                s.getValidTo(),
                s.getStatus(),
                s.getQrCodeBase64(),
                s.getPdfPath()
        );
    }
}
