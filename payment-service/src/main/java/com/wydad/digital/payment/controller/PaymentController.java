package com.wydad.digital.payment.controller;

import com.wydad.digital.payment.dto.*;
import com.wydad.digital.payment.filter.UserContext;
import com.wydad.digital.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wydad.digital.payment.dto.CardPaymentRequest;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Crédit E-cash : réservé à l'ADMIN (sinon un utilisateur peut créditer
     * son propre wallet de montant illimité sans payer).
     * Le crédit par carte passe par /card.
     */
    @PostMapping("/credit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionResponse> credit(@Valid @RequestBody CreditRequest request) {
        return ResponseEntity.ok(paymentService.credit(request));
    }

    @PostMapping("/debit")
    public ResponseEntity<TransactionResponse> debit(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        // L'email est dérivé du JWT : pas de débit du compte d'un autre utilisateur
        String email = requireSelfOrAdminEmail(null);
        return ResponseEntity.ok(paymentService.debit(email, amount, description));
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@RequestParam(required = false) String email) {
        return ResponseEntity.ok(paymentService.getBalance(requireSelfOrAdminEmail(email)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@RequestParam(required = false) String email) {
        return ResponseEntity.ok(paymentService.getTransactions(requireSelfOrAdminEmail(email)));
    }

    @PostMapping("/don")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('PARENT') or hasRole('ADMIN')")
    public ResponseEntity<?> don(@Valid @RequestBody DonRequest request) {
        // IDOR : l'email du wallet débité est TOUJOURS dérivé du JWT.
        // Un utilisateur ne peut faire un don que depuis son propre wallet ;
        // seul l'ADMIN peut cibler un autre compte.
        String effectiveEmail = requireSelfOrAdminEmail(request.email());
        DonRequest effectiveRequest = new DonRequest(
                effectiveEmail,
                request.amount(),
                request.type(),
                request.message(),
                request.recuFiscal());
        byte[] recu = paymentService.don(effectiveRequest);
        if (recu != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=recu-fiscal-" + System.currentTimeMillis() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(recu);
        }
        return ResponseEntity.ok("Don effectué avec succès");
    }

    @PostMapping("/card")
    @PreAuthorize("hasRole('ADHERENT') or hasRole('PARENT') or hasRole('ADMIN')")
    public ResponseEntity<TransactionResponse> payByCard(
            @RequestParam(required = false) String email,
            @Valid @RequestBody CardPaymentRequest request) {
        String effectiveEmail = requireSelfOrAdminEmail(email);
        return ResponseEntity.ok(paymentService.payByCard(effectiveEmail, request));
    }

    /**
     * Résout l'email cible : l'utilisateur connecté ne peut opérer que sur son
     * propre wallet ; seul l'ADMIN peut cibler un autre compte. Si le paramètre
     * est absent (ou ment sur l'identité), on retombe sur l'email du JWT.
     */
    private String requireSelfOrAdminEmail(String requestedEmail) {
        String currentEmail = UserContext.getCurrentUserEmail();
        boolean isAdmin = UserContext.isAdmin();

        if (isAdmin && requestedEmail != null && !requestedEmail.isBlank()) {
            return requestedEmail;
        }
        if (!isAdmin && requestedEmail != null && !requestedEmail.isBlank() && !requestedEmail.equals(currentEmail)) {
            throw new AccessDeniedException("Accès au wallet d'un autre utilisateur interdit");
        }
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new AccessDeniedException("Utilisateur non authentifié");
        }
        return currentEmail;
    }
}
