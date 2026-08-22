package com.wydad.digital.payment.controller;

import com.wydad.digital.payment.config.InternalSecretValidator;
import com.wydad.digital.payment.dto.InternalDebitRequest;
import com.wydad.digital.payment.exception.InsufficientFundsException;
import com.wydad.digital.payment.dto.TransactionResponse;
import com.wydad.digital.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints internes service-à-service du payment-service.
 * Protégés par le secret partagé X-Internal-Secret ; la gateway bloque
 * /api/payment/internal/** en amont, ce endpoint n'est donc jamais
 * joignable directement depuis l'extérieur.
 */
@RestController
@RequestMapping("/api/payment/internal")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;
    private final InternalSecretValidator internalSecretValidator;

    @PostMapping("/debit")
    public ResponseEntity<TransactionResponse> debit(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody InternalDebitRequest request) {
        if (!internalSecretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(paymentService.internalDebit(
                request.email(), request.amount(), request.reference()));
    }

    /** Solde insuffisant : 402 Payment Required avec le message métier. */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<String> handleInsufficientFunds(InsufficientFundsException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(e.getMessage());
    }
}
