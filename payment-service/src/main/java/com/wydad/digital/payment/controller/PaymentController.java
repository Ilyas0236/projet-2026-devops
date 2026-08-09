package com.wydad.digital.payment.controller;

import com.wydad.digital.payment.dto.*;
import com.wydad.digital.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wydad.digital.payment.dto.CardPaymentRequest;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/credit")
    public ResponseEntity<TransactionResponse> credit(@Valid @RequestBody CreditRequest request) {
        return ResponseEntity.ok(paymentService.credit(request));
    }

    @PostMapping("/debit")
    public ResponseEntity<TransactionResponse> debit(
            @RequestParam String email,
            @RequestParam java.math.BigDecimal amount,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(paymentService.debit(email, amount, description));
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@RequestParam String email) {
        return ResponseEntity.ok(paymentService.getBalance(email));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@RequestParam String email) {
        return ResponseEntity.ok(paymentService.getTransactions(email));
    }

    @PostMapping("/don")
    public ResponseEntity<?> don(@Valid @RequestBody DonRequest request) {
        byte[] recu = paymentService.don(request);
        if (recu != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=recu-fiscal-" + System.currentTimeMillis() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(recu);
        }
        return ResponseEntity.ok("Don effectué avec succès");
    }

    @PostMapping("/card")
    public ResponseEntity<TransactionResponse> payByCard(
            @RequestParam String email,
            @Valid @RequestBody CardPaymentRequest request) {
        return ResponseEntity.ok(paymentService.payByCard(email, request));
    }
}