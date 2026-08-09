package com.wydad.digital.payment.service;

import com.wydad.digital.payment.dto.CardPaymentRequest;
import com.wydad.digital.payment.dto.CardPaymentResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChariBaasService {

    private static final String TEST_CARD = "4242424242424242";
    private static final String TEST_OTP = "000000";

    public CardPaymentResponse processPayment(CardPaymentRequest request, BigDecimal amount) {
        // Validation mock
        if (!TEST_CARD.equals(request.cardNumber())) {
            return CardPaymentResponse.builder()
                    .success(false)
                    .message("Carte refusée. Utilisez la carte test : 4242424242424242")
                    .build();
        }

        if (!"123".equals(request.cvv())) {
            return CardPaymentResponse.builder()
                    .success(false)
                    .message("CVV invalide. Utilisez : 123")
                    .build();
        }

        if (!TEST_OTP.equals(request.otp())) {
            return CardPaymentResponse.builder()
                    .success(false)
                    .message("OTP invalide. Utilisez : 000000")
                    .build();
        }

        // Paiement simulé réussi
        String transactionId = "CHARI-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        return CardPaymentResponse.builder()
                .success(true)
                .message("Paiement approuvé (mock ChariBaaS)")
                .transactionId(transactionId)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .build();
    }
}